package com.boostercloud.framework.core.services;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.core.BoosterGlobalErrorDispatcher;
import com.boostercloud.framework.core.SchemaMigrator;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EntityInterface;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventStoreEntryEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.EventGlobalError;
import com.boostercloud.framework.types.errors.InvalidEventError;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.InvalidReducerError;
import com.boostercloud.framework.types.errors.ReducerGlobalError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.metadata.ClassMetadata;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Port of {@code services/event-store.ts}: rebuilds the current state (snapshot) of an entity and marks events as
 * dispatched. Stateless apart from the configuration; a new instance is created per dispatch, per
 * {@code Booster.entity} call and per data migration.
 *
 * <p>{@link #fetchEntitySnapshot(String, UUID)}:
 *
 * <ol>
 *   <li>loads the latest snapshot ({@code provider.events().latestEntitySnapshot(config, name, id)}) and migrates its
 *       schema ({@link SchemaMigrator});</li>
 *   <li>loads the events stored after the snapshot's {@code snapshottedEventCreatedAt} (the origin of time,
 *       {@value #ORIGIN_OF_TIME}, without snapshot) with {@code provider.events().forEntitySince(config, name, id,
 *       since)}; without pending events the loaded snapshot is returned as is (nothing is stored);</li>
 *   <li>reduces the pending events strictly in the order the provider returned them, one at a time (entries that are
 *       not events are skipped). {@code BoosterEntityTouched}/{@code BoosterEntityMigrated} events with super kind
 *       {@code booster} are reduced by the framework; any other event is schema-migrated, bound to its class and passed
 *       with the current entity instance ({@code null} for the first event) to the static reducer registered by
 *       {@code @Reduces};</li>
 *   <li>stores the final snapshot (one per call) with {@link Retrier#retryWithBackoff retry/backoff}
 *       ({@code config.eventStoreRetry}) and returns the stored envelope — or nothing when storing failed after every
 *       attempt (logged, never thrown).</li>
 * </ol>
 *
 * <p>Errors while reducing: an unregistered event type ({@link InvalidEventError}) goes to the global error handler
 * as an {@link EventGlobalError}; when the handler swallows it the event is skipped, otherwise the fetch fails. Any
 * failure inside a reduction (the reducer lookup, the user reducer, the snapshot construction) goes to the global
 * error handler as a {@link ReducerGlobalError} and the error it returns is thrown — when it returns none, an
 * {@link IgnoredReducerError} is thrown (TypeScript throws {@code undefined}): reducer errors always stop the
 * reduction. A missing reducer ({@link InvalidReducerError}) that comes back from the handler is dispatched a second
 * time from the reduction loop, which then skips the event when the handler swallows it (as in TypeScript). Every
 * other error fails the fetch and nothing is stored.
 *
 * <p>The provider-facing steps are package-private, non-final methods so that tests can stub them on a spy, as the
 * TypeScript tests replace the private methods. Trace points: {@code FETCH_ENTITY_SNAPSHOT},
 * {@code CUSTOM} ({@link #storeDispatchedEvent}), {@code STORE_SNAPSHOT}, {@code LOAD_LATEST_SNAPSHOT},
 * {@code LOAD_EVENT_STREAM_SINCE}, {@code ENTITY_REDUCER}.
 */
public class EventStore {

  /** {@code new Date(0).toISOString()}: where an entity without snapshot starts being reduced. */
  public static final String ORIGIN_OF_TIME = "1970-01-01T00:00:00.000Z";

  /** The {@code booster} events reduced by the framework instead of a user reducer. */
  static final List<String> BOOSTER_REDUCIBLE_EVENTS_TYPES_NAMES =
      List.of(BoosterEntityMigrated.class.getSimpleName(), BoosterEntityTouched.class.getSimpleName());

  /** JavaScript's {@code Date.prototype.toString()} format, used in the snapshot storage error message. */
  private static final DateTimeFormatter JS_DATE_FORMAT =
      DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'xx (zzzz)", Locale.US);

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public EventStore(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code fetchEntitySnapshot(entityName, entityID)}: the current state of an entity, reducing and storing the
   * events stored since its latest snapshot (see the class documentation).
   *
   * <p>A {@code BoosterEntityMigrated} event makes the result a snapshot of <b>another</b> entity (the new entity
   * type and id); later events of the same stream are reduced on top of it.
   *
   * @param entityName the entity class name
   * @param entityID the entity id
   * @return the stored (or, without pending events, the loaded) snapshot; empty when the entity has no state yet, or
   *     when the new snapshot could not be stored
   */
  public Optional<EntitySnapshotEnvelope> fetchEntitySnapshot(String entityName, UUID entityID) {
    return Tracer.trace(TraceActionTypes.FETCH_ENTITY_SNAPSHOT.value(), EventStore.class, "fetchEntitySnapshot",
        Arrays.asList(entityName, entityID), () -> Optional.ofNullable(doFetchEntitySnapshot(entityName, entityID)));
  }

  private EntitySnapshotEnvelope doFetchEntitySnapshot(String entityName, UUID entityID) {
    Logger logger = Loggers.getLogger(config, "EventStore#fetchEntitySnapshot");
    logger.debug("Fetching snapshot for entity " + entityName + " with ID " + entityID);
    EntitySnapshotEnvelope latestSnapshotEnvelope = loadLatestSnapshot(entityName, entityID);

    String lastVisitedTime = latestSnapshotEnvelope != null && latestSnapshotEnvelope.snapshottedEventCreatedAt() != null
        ? latestSnapshotEnvelope.snapshottedEventCreatedAt()
        : ORIGIN_OF_TIME;
    List<? extends EventStoreEntryEnvelope> pendingEvents = loadEventStreamSince(entityName, entityID, lastVisitedTime);

    if (pendingEvents == null || pendingEvents.isEmpty()) {
      return latestSnapshotEnvelope;
    }
    logger.debug("Looking for the reducer for entity " + entityName + " with ID " + entityID);
    // The persistence fields of the loaded snapshot are not needed in the reduction process
    NonPersistedEntitySnapshotEnvelope newEntitySnapshot =
        latestSnapshotEnvelope == null ? null : latestSnapshotEnvelope.toNonPersisted();
    for (EventStoreEntryEnvelope pendingEntry : pendingEvents) {
      // We double-check that what we are reducing is an event
      if (!(pendingEntry instanceof EventEnvelope pendingEvent)) {
        continue;
      }
      try {
        newEntitySnapshot = entityReducer(pendingEvent, newEntitySnapshot);
      } catch (Throwable e) {
        if (e instanceof InvalidEventError) {
          Throwable error = new BoosterGlobalErrorDispatcher(config).dispatch(new EventGlobalError(pendingEvent, e));
          if (error != null) {
            throw UserCode.rethrow(error);
          }
          continue;
        } else if (e instanceof InvalidReducerError invalidReducerError) {
          ReducerMetadata reducerMetadata = config.reducers.get(pendingEvent.typeName());
          Throwable error = new BoosterGlobalErrorDispatcher(config).dispatch(new ReducerGlobalError(pendingEvent,
              invalidReducerError.eventInstance(), invalidReducerError.snapshotInstance(), reducerMetadata, e));
          if (error != null) {
            throw UserCode.rethrow(error);
          }
          continue;
        }
        throw UserCode.rethrow(e);
      }
    }

    if (newEntitySnapshot == null) {
      logger.debug("No snapshot was found or reduced, returning");
      return null;
    }

    if (!Objects.equals(newEntitySnapshot.entityID(), entityID)) {
      logger.debug("Migrated entity " + entityName + " with previous ID " + entityID + " to "
          + newEntitySnapshot.typeName() + " with the new ID " + newEntitySnapshot.entityID(), newEntitySnapshot);
    } else {
      logger.debug("Reduced new snapshot for entity " + entityName + " with ID " + entityID + ": ", newEntitySnapshot);
    }

    return storeSnapshot(newEntitySnapshot);
  }

  /**
   * {@code storeDispatchedEvent(eventEnvelope)}: records that the event is being dispatched
   * ({@code provider.events().storeDispatched(event, config)} with retry/backoff). When the provider keeps failing the
   * error is logged at debug level and {@code true} is returned, so the event is processed (at-least-once).
   *
   * @param eventEnvelope the event
   * @return {@code true} when the event must be processed, {@code false} when it had already been dispatched
   */
  public boolean storeDispatchedEvent(EventEnvelope eventEnvelope) {
    return Tracer.trace(TraceActionTypes.CUSTOM.value(), EventStore.class, "storeDispatchedEvent",
        Arrays.asList(eventEnvelope), () -> doStoreDispatchedEvent(eventEnvelope));
  }

  private boolean doStoreDispatchedEvent(EventEnvelope eventEnvelope) {
    Logger logger = Loggers.getLogger(config, "EventStore#storeDispatchedEvent");
    try {
      logger.debug("Storing event in the dispatched event store:", eventEnvelope);
      return Retrier.retryWithBackoff(() -> config.provider().events().storeDispatched(eventEnvelope, config),
          config.eventStoreRetry, logger);
    } catch (Exception e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("error", e);
      details.put("eventEnvelope", eventEnvelope);
      logger.debug("Could not store dispatched event. Continue its processing.", details);
      return true;
    }
  }

  /**
   * {@code storeSnapshot(snapshot)}: {@code provider.events().storeSnapshot(snapshot, config)} with retry/backoff.
   * Never throws: a failure after every attempt is logged at error level and {@code null} is returned.
   *
   * @param snapshot the new snapshot
   * @return the stored snapshot, or {@code null} when it could not be stored
   */
  EntitySnapshotEnvelope storeSnapshot(NonPersistedEntitySnapshotEnvelope snapshot) {
    return Tracer.trace(TraceActionTypes.STORE_SNAPSHOT.value(), EventStore.class, "storeSnapshot",
        Arrays.asList(snapshot), () -> doStoreSnapshot(snapshot));
  }

  private EntitySnapshotEnvelope doStoreSnapshot(NonPersistedEntitySnapshotEnvelope snapshot) {
    Logger logger = Loggers.getLogger(config, "EventStore#storeSnapshot");
    try {
      logger.debug("Storing snapshot in the event store:", snapshot);
      return Retrier.retryWithBackoff(() -> config.provider().events().storeSnapshot(snapshot, config),
          config.eventStoreRetry, logger);
    } catch (Exception e) {
      logger.error("The snapshot for entity " + snapshot.typeName() + " with ID " + snapshot.entityID()
          + " couldn't be stored (Tried on " + JS_DATE_FORMAT.format(Dates.now().atZone(ZoneId.systemDefault())) + ")",
          snapshot, "\nError:", e);
      return null;
    }
  }

  /**
   * {@code loadLatestSnapshot(entityName, entityID)}: the latest stored snapshot, schema-migrated.
   *
   * @param entityName the entity class name
   * @param entityID the entity id
   * @return the snapshot, or {@code null}
   */
  EntitySnapshotEnvelope loadLatestSnapshot(String entityName, UUID entityID) {
    return Tracer.trace(TraceActionTypes.LOAD_LATEST_SNAPSHOT.value(), EventStore.class, "loadLatestSnapshot",
        Arrays.asList(entityName, entityID), () -> {
          Logger logger = Loggers.getLogger(config, "EventStore#loadLatestSnapshot");
          logger.debug("Loading latest snapshot for entity " + entityName + " and ID " + entityID);
          Optional<EntitySnapshotEnvelope> latestSnapshot =
              config.provider().events().latestEntitySnapshot(config, entityName, entityID);
          if (latestSnapshot != null && latestSnapshot.isPresent()) {
            return new SchemaMigrator(config).migrate(latestSnapshot.get());
          }
          return null;
        });
  }

  /**
   * {@code loadEventStreamSince(entityTypeName, entityID, timestamp)}: the entries stored after {@code timestamp}, in
   * the provider's order. Declared with a wildcard so that the reduction loop keeps TypeScript's "skip what is not an
   * event" check.
   *
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @param timestamp an ISO timestamp
   * @return the pending entries
   */
  List<? extends EventStoreEntryEnvelope> loadEventStreamSince(String entityTypeName, UUID entityID, String timestamp) {
    return Tracer.trace(TraceActionTypes.LOAD_EVENT_STREAM_SINCE.value(), EventStore.class, "loadEventStreamSince",
        Arrays.asList(entityTypeName, entityID, timestamp), () -> {
          Logger logger = Loggers.getLogger(config, "EventStore#loadEventStreamSince");
          logger.debug("Loading list of pending events for entity " + entityTypeName + " with ID " + entityID
              + " since " + timestamp);
          return config.provider().events().forEntitySince(config, entityTypeName, entityID, timestamp);
        });
  }

  /**
   * {@code entityReducer(eventEnvelope, latestSnapshot)}: reduces one event onto the current snapshot.
   *
   * @param eventEnvelope the event
   * @param latestSnapshot the current snapshot, or {@code null}
   * @return the new snapshot, or {@code null} (a touch of an entity without state)
   */
  NonPersistedEntitySnapshotEnvelope entityReducer(EventEnvelope eventEnvelope, NonPersistedEntitySnapshotEnvelope latestSnapshot) {
    return Tracer.trace(TraceActionTypes.ENTITY_REDUCER.value(), EventStore.class, "entityReducer",
        Arrays.asList(eventEnvelope, latestSnapshot), () -> doEntityReducer(eventEnvelope, latestSnapshot));
  }

  private NonPersistedEntitySnapshotEnvelope doEntityReducer(EventEnvelope eventEnvelope,
      NonPersistedEntitySnapshotEnvelope latestSnapshot) {
    Logger logger = Loggers.getLogger(config, "entityReducer");
    logger.debug("Calling reducer with event: ", eventEnvelope, " and entity snapshot ", latestSnapshot);
    if (shouldReduceBoosterSuperKind(eventEnvelope)) {
      return reduceSuperKind(eventEnvelope, latestSnapshot);
    }

    EventMetadata eventMetadata = eventMetadataFor(eventEnvelope);
    EventEnvelope migratedEventEnvelope = new SchemaMigrator(config).migrate(eventEnvelope);
    Object eventInstance = Instances.createInstance(eventMetadata.type(), migratedEventEnvelope.value());
    EntityMetadata entityMetadata = config.entities.get(migratedEventEnvelope.entityTypeName());
    ReducerMetadata reducerMetadata = config.reducers.get(eventEnvelope.typeName());
    Object snapshotInstance = latestSnapshot != null
        ? Instances.createInstance(entityClass(entityMetadata, migratedEventEnvelope.entityTypeName()), latestSnapshot.value())
        : null;
    return createNewSnapshot(migratedEventEnvelope, eventInstance, snapshotInstance, eventEnvelope, reducerMetadata);
  }

  private boolean shouldReduceBoosterSuperKind(EventEnvelope eventEnvelope) {
    boolean reducible = BOOSTER_REDUCIBLE_EVENTS_TYPES_NAMES.contains(eventEnvelope.typeName());
    return eventEnvelope.superKind() == SuperKindType.BOOSTER && reducible;
  }

  private EventMetadata eventMetadataFor(EventEnvelope eventEnvelope) {
    Logger logger = Loggers.getLogger(config, "eventMetadataFor");
    EventMetadata eventMetadata = config.events.get(eventEnvelope.typeName());
    if (eventMetadata == null) {
      logger.error("No event registered for event " + eventEnvelope.typeName());
      throw new InvalidEventError("No event registered for event " + eventEnvelope.typeName());
    }
    return eventMetadata;
  }

  private NonPersistedEntitySnapshotEnvelope createNewSnapshot(EventEnvelope migratedEventEnvelope, Object eventInstance,
      Object snapshotInstance, EventEnvelope eventEnvelope, ReducerMetadata reducerMetadata) {
    Logger logger = Loggers.getLogger(config, "createNewSnapshot");
    try {
      Method reducer = reducerForEvent(migratedEventEnvelope.typeName(), eventInstance, snapshotInstance);
      Object newEntity = UserCode.invoke(reducer, null, eventInstance, snapshotInstance);

      NonPersistedEntitySnapshotEnvelope newSnapshot = NonPersistedEntitySnapshotEnvelope.builder()
          .version(config.currentVersionFor(eventEnvelope.entityTypeName()))
          .superKind(migratedEventEnvelope.superKind())
          .requestID(migratedEventEnvelope.requestID())
          .entityID(migratedEventEnvelope.entityID())
          .entityTypeName(migratedEventEnvelope.entityTypeName())
          .typeName(migratedEventEnvelope.entityTypeName())
          .value(newEntity)
          .snapshottedEventCreatedAt(migratedEventEnvelope.createdAt())
          .build();
      logger.debug("Reducer result: ", newSnapshot);
      return newSnapshot;
    } catch (Exception e) {
      Throwable error = new BoosterGlobalErrorDispatcher(config).dispatch(
          new ReducerGlobalError(migratedEventEnvelope, eventInstance, snapshotInstance, reducerMetadata, e));
      throw UserCode.rethrow(error != null ? error : new IgnoredReducerError(migratedEventEnvelope.typeName(),
          migratedEventEnvelope.entityTypeName(), migratedEventEnvelope.entityID(), e));
    }
  }

  private NonPersistedEntitySnapshotEnvelope reduceSuperKind(EventEnvelope eventEnvelope,
      NonPersistedEntitySnapshotEnvelope latestSnapshot) {
    if (BoosterEntityTouched.class.getSimpleName().equals(eventEnvelope.typeName())) {
      return reduceEntityTouched(eventEnvelope, latestSnapshot);
    }
    if (BoosterEntityMigrated.class.getSimpleName().equals(eventEnvelope.typeName())) {
      return reduceEntityMigrated(eventEnvelope);
    }
    throw new InvalidParameterError("Unexpected super kind " + Js.toString(eventEnvelope.superKind()) + " to be reduced");
  }

  /** The value of a {@code BoosterEntityMigrated} event is used as stored (raw); no instance, no schema migration. */
  private NonPersistedEntitySnapshotEnvelope reduceEntityMigrated(EventEnvelope eventEnvelope) {
    Object newEntity;
    Object newEntityName;
    if (eventEnvelope.value() instanceof BoosterEntityMigrated event) {
      newEntity = event.newEntity();
      newEntityName = event.newEntityName();
    } else {
      Map<String, Object> event = rawValue(eventEnvelope.value());
      newEntity = event.get("newEntity");
      newEntityName = event.get("newEntityName");
    }
    return toBoosterEntitySnapshot(eventEnvelope, newEntity, newEntityName == null ? null : Js.toString(newEntityName));
  }

  private NonPersistedEntitySnapshotEnvelope reduceEntityTouched(EventEnvelope eventEnvelope,
      NonPersistedEntitySnapshotEnvelope latestSnapshot) {
    Logger logger = Loggers.getLogger(config, "EventStore#reduceEntityTouched");
    logger.debug("Reducing ", eventEnvelope, " with latestSnapshot");
    if (latestSnapshot == null) {
      logger.debug("Latest snapshot not found, returning");
      return null;
    }
    String entityName;
    if (eventEnvelope.value() instanceof BoosterEntityTouched event) {
      entityName = event.entityName();
    } else {
      Object rawEntityName = rawValue(eventEnvelope.value()).get("entityName");
      entityName = rawEntityName == null ? null : Js.toString(rawEntityName);
    }
    EntityMetadata entityMetadata = config.entities.get(entityName);
    Object snapshotInstance = Instances.createInstance(entityClass(entityMetadata, entityName), latestSnapshot.value());
    return toBoosterEntitySnapshot(eventEnvelope, snapshotInstance, entityName);
  }

  private NonPersistedEntitySnapshotEnvelope toBoosterEntitySnapshot(EventEnvelope eventEnvelope, Object entity,
      String className) {
    Logger logger = Loggers.getLogger(config, "EventStore#toBoosterEntitySnapshot");
    NonPersistedEntitySnapshotEnvelope boosterMigratedSnapshot = NonPersistedEntitySnapshotEnvelope.builder()
        .version(config.currentVersionFor(className))
        .superKind(eventEnvelope.superKind())
        .requestID(eventEnvelope.requestID())
        .entityID(entityIdOf(entity))
        .entityTypeName(className)
        .typeName(className)
        .value(entity)
        .snapshottedEventCreatedAt(eventEnvelope.createdAt())
        .build();
    logger.debug("BoosterEntitySnapshot result: ", boosterMigratedSnapshot);
    return boosterMigratedSnapshot;
  }

  /**
   * {@code reducerForEvent(eventName, eventInstance, snapshotInstance)}: the static reducer registered for the event
   * ({@code config.reducers}), resolved for the given arguments.
   *
   * @param eventName the event class name
   * @param eventInstance the event
   * @param snapshotInstance the current entity, or {@code null}
   * @return the reducer method
   * @throws InvalidReducerError {@code No reducer registered for event <E>}
   * @throws IllegalStateException when the entity class has no static method with that name accepting the arguments
   *     (TypeScript fails with a {@code TypeError} when calling it)
   */
  Method reducerForEvent(String eventName, Object eventInstance, Object snapshotInstance) {
    Logger logger = Loggers.getLogger(config, "EventStore#reducerForEvent");
    ReducerMetadata reducerMetadata = config.reducers.get(eventName);
    if (reducerMetadata == null) {
      throw new InvalidReducerError("No reducer registered for event " + eventName, eventInstance, snapshotInstance);
    }
    String methodName = reducerMetadata.methodName();
    Class<?> entityClass = reducerMetadata.type();
    Method reducer = UserCode.findStatic(entityClass, methodName, eventInstance, snapshotInstance)
        .orElseThrow(() -> new IllegalStateException("The reducer " + entityClass.getSimpleName() + "." + methodName
            + " registered for event " + eventName + " is not a static method that accepts ("
            + typeNameOf(eventInstance) + ", " + typeNameOf(snapshotInstance) + ")"));
    logger.debug("Found reducer for event " + eventName + ": \"" + entityClass.getSimpleName() + "." + methodName + "\"");
    return reducer;
  }

  /** TypeScript reads {@code entityMetadata.class}; a missing registration is a {@code TypeError} there. */
  private static Class<?> entityClass(EntityMetadata entityMetadata, String entityTypeName) {
    if (entityMetadata == null || entityMetadata.type() == null) {
      throw new IllegalStateException("No entity registered with the name " + entityTypeName
          + ": cannot create an instance of its snapshot");
    }
    return entityMetadata.type();
  }

  /** {@code entity.id}: the {@code id} of a raw map, an {@link EntityInterface} or any record/class with that field. */
  private static UUID entityIdOf(Object entity) {
    Object id = switch (entity) {
      case null -> throw new IllegalStateException("Cannot read the id of an undefined entity");
      case Map<?, ?> raw -> raw.get("id");
      case EntityInterface instance -> instance.id();
      default -> ClassMetadata.of(entity.getClass()).field("id").map(field -> field.readValue(entity)).orElse(null);
    };
    return switch (id) {
      case null -> null;
      case UUID uuid -> uuid;
      default -> UUID.of(Js.toString(id));
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> rawValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    if (value == null) {
      return Map.of();
    }
    return BoosterJson.toRawMap(value);
  }

  private static String typeNameOf(Object value) {
    return value == null ? "null" : value.getClass().getSimpleName();
  }
}
