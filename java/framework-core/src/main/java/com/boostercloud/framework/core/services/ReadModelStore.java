package com.boostercloud.framework.core.services;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.core.BoosterGlobalErrorDispatcher;
import com.boostercloud.framework.core.BoosterReadModelsReader;
import com.boostercloud.framework.core.ReadModelSchemaMigrator;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.JoinKey;
import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelAction;
import com.boostercloud.framework.types.concepts.ReadModelJoinKeyFunction;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import com.boostercloud.framework.types.errors.ProjectionGlobalError;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Port of {@code services/read-model-store.ts}: the write side of read models. For an entity snapshot it finds the
 * projections (un-projections when an event is deleted), the affected read models, runs the user projection methods
 * and stores, deletes or skips the results with optimistic concurrency and retries.
 *
 * <p>How {@link #project(EntitySnapshotEnvelope, boolean)} works:
 *
 * <ol>
 *   <li>The projections of the entity type ({@code config.projections}), or its un-projections when
 *       {@code deleteEvent} is set: then every projection must have an un-projection with the same read model and
 *       join key ({@code Missing UnProjections for entity <E>} / {@code Missing UnProjection for ReadModel <RM> with
 *       joinKey <key> for entity <E>}). No list: nothing happens.</li>
 *   <li>The entity is an instance of its class ({@code Instances.createInstance}); every projection runs
 *       concurrently and the call waits for all of them ({@link Promises}, failures are aggregated in a
 *       {@code PromisesError}).</li>
 *   <li>The affected read models are searched without authorization or hooks: {@code { id: { in: [ids] } }} for an
 *       entity-property join key (a collection value gives one id per element), or the filter returned by the
 *       join-key function. The stored documents are schema-migrated.</li>
 *   <li>One task per affected read model, each with its own retry loop on
 *       {@link OptimisticConcurrencyUnexpectedVersionError} (1000 attempts, no delay; from the second attempt the read
 *       model is fetched again by id and sequence key). The projection is a static method called by name: {@code
 *       (entity, currentReadModel, projectionInfo)} for a scalar entity property, {@code (entity, readModelID,
 *       currentReadModel, projectionInfo)} for a collection property, {@code (entity, readModelID, currentReadModel)}
 *       for a join-key function; {@code currentReadModel} is an instance of the read model class, or {@code null};
 *       {@code projectionInfo.reason} is {@code ENTITY_DELETED} for un-projections. Methods may declare fewer
 *       parameters. A failure goes through the global error handler.</li>
 *   <li>{@link ReadModelAction#DELETE} deletes the current document (also when there is none: the provider receives
 *       {@code null}); {@link ReadModelAction#NOTHING} does nothing; anything else (a {@code ProjectionResult.of(x)},
 *       or a plain object) is stored as a raw document with {@code boosterMetadata = { ...previous, version:
 *       previous + 1, schemaVersion, lastUpdateAt, lastProjectionInfo }} and the previous version (0 when new) as the
 *       expected one.</li>
 * </ol>
 *
 * <p>Read models are handled as raw documents ({@code Map<String, Object>} with {@code boosterMetadata}) internally,
 * so the version and the provider-owned metadata (e.g. {@code optimisticConcurrencyValue}) survive even when the read
 * model class does not declare them. The public {@link #getReadModels} and {@link #fetchReadModel} return instances,
 * as in TypeScript.
 */
public class ReadModelStore {

  private static final String BOOSTER_METADATA = "boosterMetadata";

  /** Marks a projection error swallowed by the global error handler. */
  private static final Object SWALLOWED = new Object();

  private final BoosterConfig config;

  /**
   * A projection method (TypeScript {@code Function}): called with the arguments described in
   * {@link ReadModelStore}; extra arguments are ignored by methods that declare fewer parameters. The result is a
   * {@link ProjectionResult}, a read model, or {@code null}.
   */
  @FunctionalInterface
  public interface ProjectionFunction {
    /**
     * @param arguments the arguments
     * @return what the projection returns
     */
    Object call(Object... arguments);
  }

  /**
   * @param config the configuration
   */
  public ReadModelStore(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code project(entitySnapshotEnvelope)} ({@code deleteEvent = false}).
   *
   * @param entitySnapshotEnvelope the new snapshot
   */
  public void project(EntitySnapshotEnvelope entitySnapshotEnvelope) {
    project(entitySnapshotEnvelope, false);
  }

  /**
   * {@code project(entitySnapshotEnvelope, deleteEvent)}.
   *
   * @param entitySnapshotEnvelope the snapshot
   * @param deleteEvent {@code true} to run the un-projections (an event of the entity is being deleted)
   * @throws IllegalStateException when un-projections are missing for a delete
   * @throws com.boostercloud.framework.common.PromisesError when a projection failed (after all of them finished)
   */
  public void project(EntitySnapshotEnvelope entitySnapshotEnvelope, boolean deleteEvent) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#project");
    String entityTypeName = entitySnapshotEnvelope.entityTypeName();
    List<ProjectionMetadata> projections =
        deleteEvent ? getUnProjections(entitySnapshotEnvelope) : entityProjections(entitySnapshotEnvelope);
    if (projections == null) {
      logger.debug("No projections found for entity " + entityTypeName + ". Skipping...");
      return;
    }
    logger.debug("Projections found for entity " + entityTypeName + ": ", projections);
    EntityMetadata entityMetadata = config.entities.get(entityTypeName);
    if (entityMetadata == null) {
      throw new IllegalStateException("Could not find the entity " + entityTypeName + " (is it annotated with @Entity?)");
    }
    Object entityInstance = Instances.createInstance(entityMetadata.type(), entitySnapshotEnvelope.value());
    Promises.mapAllSettledAndFulfilled(projections, projectionMetadata -> {
      logger.debug("Projecting entity snapshot ", entitySnapshotEnvelope,
          " to build new state of read model with projectionMetadata ", projectionMetadata);
      String readModelName = projectionMetadata.type().getSimpleName();
      SequenceKey sequenceKey = sequenceKeyForProjection(entityInstance, projectionMetadata);
      return projectEntity(entityInstance, projectionMetadata, entityMetadata, entitySnapshotEnvelope, readModelName,
          deleteEvent, sequenceKey);
    });
  }

  /**
   * {@code getReadModels(entityInstance, projectionMetadata, entityMetadata)}: the read models matched by a
   * projection's join key (searched without authorization or hooks, schema-migrated), as instances of the read model
   * class; empty when the entity has no join key value.
   *
   * @param entityInstance the entity
   * @param projectionMetadata the projection
   * @param entityMetadata the entity metadata
   * @return the read model instances
   */
  public List<Object> getReadModels(Object entityInstance, ProjectionMetadata projectionMetadata, EntityMetadata entityMetadata) {
    List<Object> readModels = new ArrayList<>();
    for (Map<String, Object> document : findReadModels(entityInstance, projectionMetadata, entityMetadata)) {
      readModels.add(Instances.createInstance(projectionMetadata.type(), document));
    }
    return readModels;
  }

  /**
   * {@code fetchReadModel(readModelName, readModelID, sequenceKey?)}: the stored read model, as an instance of its
   * class (not migrated). No id: empty, without calling the provider.
   *
   * @param readModelName the read model class name
   * @param readModelID the id, or {@code null}
   * @param sequenceKey the sequence key, or {@code null}
   * @return the read model instance, if found
   * @throws IllegalStateException {@code Got multiple objects for a request by Id. If this is a sequenced read model
   *     you should also specify the sequenceKey field.} when the provider returns several read models
   */
  public Optional<Object> fetchReadModel(String readModelName, UUID readModelID, SequenceKey sequenceKey) {
    return fetchReadModelDocument(readModelName, readModelID, sequenceKey)
        .map(document -> Instances.createInstance(readModelMetadata(readModelName).type(), document));
  }

  /**
   * {@code getProjectionFunction(projectionMetadata)}: the static projection method of the read model class, found
   * by name when it is called ({@link UserCode}, so {@code @Trace} on it works). A missing method fails when called,
   * inside the projection's error handling, like calling {@code undefined} in TypeScript.
   *
   * @param projectionMetadata the projection
   * @return the function
   */
  public ProjectionFunction getProjectionFunction(ProjectionMetadata projectionMetadata) {
    Class<?> readModelClass = projectionMetadata.type();
    if (readModelClass == null) {
      throw new IllegalStateException("Couldn't load the ReadModel class of the projection " + projectionMetadata.methodName());
    }
    return arguments -> UserCode.invokeStatic(readModelClass, projectionMetadata.methodName(), arguments);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Projections and un-projections
  // ---------------------------------------------------------------------------------------------------------------

  private List<ProjectionMetadata> getUnProjections(EntitySnapshotEnvelope entitySnapshotEnvelope) {
    List<ProjectionMetadata> unProjections = entityUnProjections(entitySnapshotEnvelope);
    List<ProjectionMetadata> projections = entityProjections(entitySnapshotEnvelope);
    if (projections != null && !projections.isEmpty()) {
      if (unProjections == null) {
        throw new IllegalStateException("Missing UnProjections for entity " + entitySnapshotEnvelope.entityTypeName());
      }
      ProjectionMetadata missingProjection = findFirstMissingProjection(projections, unProjections);
      if (missingProjection != null) {
        throw new IllegalStateException("Missing UnProjection for ReadModel " + missingProjection.type().getSimpleName()
            + " with joinKey " + missingProjection.joinKey() + " for entity " + entitySnapshotEnvelope.entityTypeName());
      }
    }
    return unProjections;
  }

  private List<ProjectionMetadata> entityProjections(EntitySnapshotEnvelope entitySnapshotEnvelope) {
    return config.projections.get(entitySnapshotEnvelope.entityTypeName());
  }

  private List<ProjectionMetadata> entityUnProjections(EntitySnapshotEnvelope entitySnapshotEnvelope) {
    return config.unProjections.get(entitySnapshotEnvelope.entityTypeName());
  }

  private static ProjectionMetadata findFirstMissingProjection(List<ProjectionMetadata> sources, List<ProjectionMetadata> to) {
    for (ProjectionMetadata source : sources) {
      if (!someProjection(to, source)) {
        return source;
      }
    }
    return null;
  }

  /** Same read model class name and same join key text (TypeScript compares {@code joinKey.toString()}). */
  private static boolean someProjection(List<ProjectionMetadata> sources, ProjectionMetadata to) {
    for (ProjectionMetadata source : sources) {
      if (source.type().getSimpleName().equals(to.type().getSimpleName())
          && String.valueOf(source.joinKey()).equals(String.valueOf(to.joinKey()))) {
        return true;
      }
    }
    return false;
  }

  /**
   * {@code sequenceKeyForProjection(entity, projectionMetadata)}: {@code { name, value: entity[name] }} when the read
   * model is sequenced ({@code config.readModelSequenceKeys}) and the entity has a truthy value for that property.
   *
   * @param entity the entity instance
   * @param projectionMetadata the projection
   * @return the sequence key, or {@code null}
   */
  SequenceKey sequenceKeyForProjection(Object entity, ProjectionMetadata projectionMetadata) {
    String sequenceKeyName = config.readModelSequenceKeys.get(projectionMetadata.type().getSimpleName());
    Object sequenceKeyValue = sequenceKeyName == null ? null : readProperty(entity, sequenceKeyName);
    if (Js.isTruthy(sequenceKeyName) && Js.isTruthy(sequenceKeyValue)) {
      TimeKey value = sequenceKeyValue instanceof TimeKey timeKey ? timeKey : TimeKey.of(Js.toString(BoosterJson.toRaw(sequenceKeyValue)));
      return new SequenceKey(sequenceKeyName, value);
    }
    return null;
  }

  private List<Object> projectEntity(Object entityInstance, ProjectionMetadata projectionMetadata,
      EntityMetadata entityMetadata, EntitySnapshotEnvelope entitySnapshotEnvelope, String readModelName,
      boolean deleteEvent, SequenceKey sequenceKey) {
    List<Map<String, Object>> currentReadModels = findReadModels(entityInstance, projectionMetadata, entityMetadata);
    List<CompletableFuture<Object>> projections = projectionsForReadModels(entitySnapshotEnvelope, readModelName,
        sequenceKey, projectionMetadata, entityInstance, entityMetadata, deleteEvent, currentReadModels);
    return Promises.allSettledAndFulfilled(projections);
  }

  /**
   * The tasks of one projection, already started. Entity-property join key: one task per id of the entity value,
   * with the existing read model of that id; each existing read model (several for a sequenced read model) gets its
   * own task, and an id without an existing read model gets one task without it. Join-key function: one task per
   * existing read model, or a single task without read model.
   */
  private List<CompletableFuture<Object>> projectionsForReadModels(EntitySnapshotEnvelope entitySnapshotEnvelope,
      String readModelName, SequenceKey sequenceKey, ProjectionMetadata projectionMetadata, Object entityInstance,
      EntityMetadata entityMetadata, boolean deleteEvent, List<Map<String, Object>> currentReadModels) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#projectionsForReadModels");
    List<CompletableFuture<Object>> projections = new ArrayList<>();
    if (projectionMetadata.joinKey() instanceof JoinKey.EntityProperty property) {
      Object entityJoinKey = readProperty(entityInstance, property.propertyName());
      if (!Js.isTruthy(entityJoinKey)) {
        logger.warn("Couldn't find the joinKey named " + property.propertyName() + " in entity snapshot of "
            + entityMetadata.type().getSimpleName() + ". Skipping...");
        return projections;
      }
      List<UUID> entitiesJoinKeys = joinKeyIds(entityJoinKey);
      // A new read model with a join key by entity is projected with the id from the entity
      if (currentReadModels.isEmpty()) {
        for (UUID readModelId : entitiesJoinKeys) {
          projections.add(startProjection(entitySnapshotEnvelope, readModelName, sequenceKey, entityInstance,
              projectionMetadata, deleteEvent, readModelId, null));
        }
        return projections;
      }
      Set<UUID> existingIds = new HashSet<>();
      for (Map<String, Object> currentReadModel : currentReadModels) {
        existingIds.add(readModelId(currentReadModel));
      }
      Set<UUID> scheduledWithoutReadModel = new HashSet<>();
      for (Map<String, Object> currentReadModel : currentReadModels) {
        UUID currentReadModelId = readModelId(currentReadModel);
        for (UUID readModelId : entitiesJoinKeys) {
          if (readModelId != null && readModelId.equals(currentReadModelId)) {
            projections.add(startProjection(entitySnapshotEnvelope, readModelName, sequenceKey, entityInstance,
                projectionMetadata, deleteEvent, readModelId, currentReadModel));
          } else if (!existingIds.contains(readModelId) && scheduledWithoutReadModel.add(readModelId)) {
            projections.add(startProjection(entitySnapshotEnvelope, readModelName, sequenceKey, entityInstance,
                projectionMetadata, deleteEvent, readModelId, null));
          }
        }
      }
      return projections;
    }
    // A read model with a join key by read model query: no previous read model nor id when nothing matched
    if (currentReadModels.isEmpty()) {
      projections.add(startProjection(entitySnapshotEnvelope, readModelName, sequenceKey, entityInstance,
          projectionMetadata, deleteEvent, null, null));
    }
    for (Map<String, Object> currentReadModel : currentReadModels) {
      projections.add(startProjection(entitySnapshotEnvelope, readModelName, sequenceKey, entityInstance,
          projectionMetadata, deleteEvent, readModelId(currentReadModel), currentReadModel));
    }
    return projections;
  }

  private CompletableFuture<Object> startProjection(EntitySnapshotEnvelope entitySnapshotEnvelope, String readModelName,
      SequenceKey sequenceKey, Object entityInstance, ProjectionMetadata projectionMetadata, boolean deleteEvent,
      UUID readModelId, Map<String, Object> currentReadModel) {
    return Promises.start(() -> projectAndStoreReadModelWithRetry(entitySnapshotEnvelope, readModelName, sequenceKey,
        entityInstance, projectionMetadata, deleteEvent, readModelId, currentReadModel));
  }

  private Object projectAndStoreReadModelWithRetry(EntitySnapshotEnvelope entitySnapshotEnvelope, String readModelName,
      SequenceKey sequenceKey, Object entityInstance, ProjectionMetadata projectionMetadata, boolean deleteEvent,
      UUID readModelId, Map<String, Object> currentReadModel) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#projectAndStoreReadModelWithRetry");
    UUID id = currentReadModel != null && readModelId(currentReadModel) != null ? readModelId(currentReadModel) : readModelId;
    logger.debug("Projecting entity snapshot ", entitySnapshotEnvelope,
        " to build new state of read model " + readModelName + " with ID " + id,
        sequenceKey != null ? " sequencing by " + sequenceKey.name() + " with value " + sequenceKey.value() : "");
    return Retrier.retryIfError(
        tryNumber -> applyProjectionToReadModel(entitySnapshotEnvelope, entityInstance, projectionMetadata, deleteEvent,
            currentReadModel, entitySnapshotEnvelope, readModelId, sequenceKey, tryNumber),
        OptimisticConcurrencyUnexpectedVersionError.class,
        logger);
  }

  /**
   * {@code applyProjectionToReadModel(...)} (TypeScript argument order): one attempt of one read model task.
   *
   * @param entitySnapshotEnvelope the snapshot
   * @param entity the entity instance
   * @param projectionMetadata the projection
   * @param deleteEvent whether an un-projection runs
   * @param currentReadModel the stored document found by the search, or {@code null}
   * @param lastProjectedEntity the snapshot (for {@code lastProjectionInfo})
   * @param currentReadModelID the read model id, or {@code null}
   * @param sequenceKey the sequence key, or {@code null}
   * @param tryNumber the attempt number (1-based)
   * @return {@code null}
   */
  @SuppressWarnings("unchecked")
  Object applyProjectionToReadModel(EntitySnapshotEnvelope entitySnapshotEnvelope, Object entity,
      ProjectionMetadata projectionMetadata, boolean deleteEvent, Map<String, Object> currentReadModel,
      EntitySnapshotEnvelope lastProjectedEntity, UUID currentReadModelID, SequenceKey sequenceKey, int tryNumber) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#applyProjectionToReadModel");
    String readModelName = projectionMetadata.type().getSimpleName();
    UUID readModelID = currentReadModelID != null ? currentReadModelID : readModelId(currentReadModel);
    String sequenceText = sequenceKey != null ? " and sequence key " + sequenceKey.name() + " = " + sequenceKey.value() : "";

    if (tryNumber > 1) {
      // In case of optimistic concurrency error, we need to fetch the current read model version and retry
      logger.debug("OptimisticConcurrencyUnexpectedVersionError (version=" + versionText(currentReadModel)
          + " and expectedDatabaseVersion=" + version(currentReadModel) + "). Looking for an updated version of read model "
          + readModelName + " with ID = " + readModelID + sequenceText);
      currentReadModel = fetchReadModelDocument(readModelName, readModelID, sequenceKey).orElse(null);
      logger.debug("Current read model " + readModelName + " with ID " + readModelID + " updated with version = "
          + versionText(currentReadModel) + sequenceText);
    }
    Map<String, Object> migratedReadModel = null;
    if (currentReadModel != null) {
      migratedReadModel = new ReadModelSchemaMigrator(config).migrate(currentReadModel, readModelName);
    }
    long currentDatabaseVersion = version(migratedReadModel);
    Object migratedReadModelInstance =
        migratedReadModel == null ? null : Instances.createInstance(projectionMetadata.type(), migratedReadModel);

    ProjectionInfo projectionInfo = new ProjectionInfo(
        deleteEvent ? ProjectionInfoReason.ENTITY_DELETED : ProjectionInfoReason.ENTITY_PROJECTED);
    Object newReadModel = callProjectionFunction(entitySnapshotEnvelope, projectionMetadata, entity,
        migratedReadModelInstance, readModelID, projectionInfo);
    if (newReadModel == SWALLOWED) {
      logger.debug("The global error handler ignored the error of " + projectionMetadata.projectionMethod()
          + ". Skipping actions for " + readModelName + " with ID " + readModelID);
      return null;
    }

    ProjectionResult<Object> result = ProjectionResult.fromReturnValue(newReadModel);
    if (result == null) {
      throw new IllegalStateException("The projection " + projectionMetadata.projectionMethod() + " returned null for the"
          + " read model " + readModelName + " with ID " + readModelID + ". Return ReadModelAction.NOTHING (or"
          + " ProjectionResult.nothing()) to leave it unchanged");
    }
    if (result == ReadModelAction.DELETE) {
      logger.debug("Deleting read model " + readModelName + " with ID " + readModelID + ":", migratedReadModel);
      config.provider().readModels().delete(config, readModelName, migratedReadModel);
      return null;
    } else if (result == ReadModelAction.NOTHING) {
      logger.debug("Skipping actions for " + readModelName + " with ID " + readModelID + ":", result);
      return null;
    }
    Object value = ((ProjectionResult.Upsert<Object>) result).value();
    return store(entity, projectionMetadata, readModelID, readModelName, migratedReadModel, value, currentDatabaseVersion,
        lastProjectedEntity);
  }

  private Object store(Object entity, ProjectionMetadata projectionMetadata, UUID readModelID, String readModelName,
      Map<String, Object> migratedReadModel, Object newReadModel, long expectedCurrentDatabaseVersion,
      EntitySnapshotEnvelope lastProjectedEntity) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#store");
    Map<String, Object> previousMetadata = boosterMetadataOf(migratedReadModel);
    Object schemaVersion = previousMetadata.get("schemaVersion") != null
        ? previousMetadata.get("schemaVersion")
        : config.currentVersionFor(readModelName);
    // Increment the read model version in 1 before storing
    long newReadModelVersion = expectedCurrentDatabaseVersion + 1;

    Map<String, Object> lastProjectionInfo = new LinkedHashMap<>();
    putIfPresent(lastProjectionInfo, "entityId", BoosterJson.toRaw(readProperty(entity, "id")));
    putIfPresent(lastProjectionInfo, "entityName", lastProjectedEntity == null ? null : lastProjectedEntity.entityTypeName());
    putIfPresent(lastProjectionInfo, "entityUpdatedAt", lastProjectedEntity == null ? null : lastProjectedEntity.createdAt());
    lastProjectionInfo.put("projectionMethod", projectionMetadata.type().getSimpleName() + "." + projectionMetadata.methodName());

    Map<String, Object> boosterMetadata = new LinkedHashMap<>(previousMetadata);
    boosterMetadata.put("version", newReadModelVersion);
    boosterMetadata.put("schemaVersion", schemaVersion);
    boosterMetadata.put("lastUpdateAt", Dates.nowIso());
    boosterMetadata.put("lastProjectionInfo", lastProjectionInfo);

    Map<String, Object> document = toMutableRawMap(newReadModel);
    document.put(BOOSTER_METADATA, boosterMetadata);
    logger.debug("Storing new version of read model " + readModelName + " with ID " + readModelID + ", version "
        + newReadModelVersion + " and expected database version " + expectedCurrentDatabaseVersion + ":", document);
    config.provider().readModels().store(config, readModelName, document, expectedCurrentDatabaseVersion);
    return null;
  }

  /**
   * {@code callProjectionFunction}: calls the projection with the shape of its join key; a failure is dispatched once
   * to the global error handler ({@code ProjectionGlobalError}) and rethrown unless the handler swallows it.
   */
  private Object callProjectionFunction(EntitySnapshotEnvelope entitySnapshotEnvelope,
      ProjectionMetadata projectionMetadata, Object entity, Object migratedReadModel, UUID readModelID,
      ProjectionInfo projectionInfo) {
    try {
      ProjectionFunction projectionFunction = getProjectionFunction(projectionMetadata);
      if (projectionMetadata.joinKey() instanceof JoinKey.EntityProperty property) {
        return isArray(readProperty(entity, property.propertyName()))
            ? projectionFunction.call(entity, readModelID, migratedReadModel, projectionInfo)
            : projectionFunction.call(entity, migratedReadModel, projectionInfo);
      }
      return projectionFunction.call(entity, readModelID, migratedReadModel);
    } catch (Exception e) {
      BoosterGlobalErrorDispatcher globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config);
      Throwable error = globalErrorDispatcher.dispatch(
          new ProjectionGlobalError(entitySnapshotEnvelope, entity, migratedReadModel, projectionMetadata, e));
      if (error != null) {
        throw UserCode.rethrow(error);
      }
      return SWALLOWED;
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Finding read models
  // ---------------------------------------------------------------------------------------------------------------

  /** The migrated documents a projection affects ({@code getReadModels} before the instantiation). */
  private List<Map<String, Object>> findReadModels(Object entityInstance, ProjectionMetadata projectionMetadata,
      EntityMetadata entityMetadata) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#getReadModels");
    logger.debug("Looking for ReadModels for entity ", entityInstance, " using Filter " + projectionMetadata.joinKey());
    String readModelName = projectionMetadata.type().getSimpleName();
    Map<String, Object> filter = filterForProjection(entityInstance, projectionMetadata, entityMetadata);
    if (filter == null) {
      return List.of();
    }
    ReadModelMetadata readModelMetadata = readModelMetadata(readModelName);
    logger.debug("Calling ReadModelSearcher searching for ReadModels for entity " + readModelName + " using Filter ", filter);
    List<Map<String, Object>> rawReadModels =
        new BoosterReadModelsReader(config).readModelSearchDocuments(readModelMetadata.type(), filter);
    return rawReadModels == null ? List.of() : rawReadModels;
  }

  /**
   * The stored document of a read model ({@code fetchReadModel} before the instantiation).
   *
   * @param readModelName the read model name
   * @param readModelID the id, or {@code null}
   * @param sequenceKey the sequence key, or {@code null}
   * @return the document
   */
  Optional<Map<String, Object>> fetchReadModelDocument(String readModelName, UUID readModelID, SequenceKey sequenceKey) {
    if (!Js.isTruthy(readModelID)) {
      return Optional.empty();
    }
    List<Map<String, Object>> rawReadModels =
        config.provider().readModels().fetch(config, readModelName, readModelID, sequenceKey);
    if (rawReadModels != null && !rawReadModels.isEmpty()) {
      if (rawReadModels.size() > 1) {
        throw new IllegalStateException("Got multiple objects for a request by Id. If this is a sequenced read model you"
            + " should also specify the sequenceKey field.");
      }
      Map<String, Object> rawReadModel = rawReadModels.get(0);
      if (rawReadModel != null) {
        return Optional.of(rawReadModel);
      }
    }
    return Optional.empty();
  }

  private Map<String, Object> filterForProjection(Object entity, ProjectionMetadata projectionMetadata,
      EntityMetadata entityMetadata) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#filterForProjection");
    logger.debug("Calculating filter for projection for ReadModels using Filter " + projectionMetadata.joinKey());
    if (projectionMetadata.joinKey() instanceof JoinKey.EntityProperty) {
      return filterForEntityProjection(entity, projectionMetadata, entityMetadata);
    }
    return filterForReadModelProjection(entity, projectionMetadata, entityMetadata);
  }

  private Map<String, Object> filterForEntityProjection(Object entity, ProjectionMetadata projectionMetadata,
      EntityMetadata entityMetadata) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#filterForEntityProjection");
    String joinKey = ((JoinKey.EntityProperty) projectionMetadata.joinKey()).propertyName();
    Object entityJoinKey = readProperty(entity, joinKey);
    if (!Js.isTruthy(entityJoinKey)) {
      logger.warn("Couldn't find the joinKey " + joinKey + " in entity snapshot of " + entityMetadata.type().getSimpleName()
          + ". Skipping...");
      return null;
    }
    List<Object> ids = new ArrayList<>();
    for (Object id : asList(entityJoinKey)) {
      ids.add(BoosterJson.toRaw(id));
    }
    if (ids.isEmpty()) {
      logger.debug("No ids found for entity projection. Skipping...");
      return null;
    }
    logger.debug("Filtering for entity projection with ids ", ids);
    Map<String, Object> in = new LinkedHashMap<>();
    in.put("in", ids);
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("id", in);
    return filter;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Map<String, Object> filterForReadModelProjection(Object entity, ProjectionMetadata projectionMetadata,
      EntityMetadata entityMetadata) {
    Logger logger = Loggers.getLogger(config, "ReadModelStore#filterForReadModelProjection");
    ReadModelJoinKeyFunction joinKeyForProjection = ((JoinKey.Query) projectionMetadata.joinKey()).function();
    if (joinKeyForProjection == null) {
      logger.warn("Couldn't find the joinKey " + projectionMetadata.joinKey() + " in entity snapshot of "
          + entityMetadata.type().getSimpleName() + ". Skipping...");
      return null;
    }
    return joinKeyForProjection.joinKey(entity);
  }

  private ReadModelMetadata readModelMetadata(String readModelName) {
    ReadModelMetadata readModelMetadata = config.readModels.get(readModelName);
    if (readModelMetadata == null) {
      throw new IllegalStateException("Could not find the read model " + readModelName + " (is it annotated with @ReadModel?)");
    }
    return readModelMetadata;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Dynamic values
  // ---------------------------------------------------------------------------------------------------------------

  /** {@code instance[property]}: a map key, or the property (field, component or calculated field) of an instance. */
  static Object readProperty(Object instance, String property) {
    if (instance == null || property == null) {
      return null;
    }
    if (instance instanceof Map<?, ?> map) {
      return map.get(property);
    }
    Optional<PropertyMetadata> metadata = ClassMetadata.of(instance.getClass()).field(property);
    return metadata.map(propertyMetadata -> UserCode.await(propertyMetadata.readValue(instance))).orElse(null);
  }

  private static boolean isArray(Object value) {
    return value instanceof Collection<?> || (value != null && value.getClass().isArray());
  }

  private static List<Object> asList(Object value) {
    List<Object> values = new ArrayList<>();
    if (value instanceof Collection<?> collection) {
      values.addAll(collection);
    } else if (value != null && value.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(value); i++) {
        values.add(Array.get(value, i));
      }
    } else {
      values.add(value);
    }
    return values;
  }

  private static List<UUID> joinKeyIds(Object entityJoinKey) {
    List<UUID> ids = new ArrayList<>();
    for (Object id : asList(entityJoinKey)) {
      ids.add(toUUID(id));
    }
    return ids;
  }

  private static UUID toUUID(Object id) {
    return switch (id) {
      case null -> null;
      case UUID uuid -> uuid;
      default -> UUID.of(Js.toString(BoosterJson.toRaw(id)));
    };
  }

  /** {@code readModel?.id}. */
  private static UUID readModelId(Map<String, Object> readModel) {
    return readModel == null ? null : toUUID(readModel.get("id"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> boosterMetadataOf(Map<String, Object> readModel) {
    Object metadata = readModel == null ? null : readModel.get(BOOSTER_METADATA);
    return switch (metadata) {
      case null -> Map.of();
      case Map<?, ?> map -> (Map<String, Object>) map;
      default -> BoosterJson.toRawMap(metadata);
    };
  }

  /** {@code readModel?.boosterMetadata?.version ?? 0}. */
  private static long version(Map<String, Object> readModel) {
    return boosterMetadataOf(readModel).get("version") instanceof Number version ? version.longValue() : 0;
  }

  private static String versionText(Map<String, Object> readModel) {
    Object version = boosterMetadataOf(readModel).get("version");
    return version == null ? "undefined" : Js.toString(version);
  }

  private static Map<String, Object> toMutableRawMap(Object value) {
    return new LinkedHashMap<>(BoosterJson.toRawMap(value));
  }

  private static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
