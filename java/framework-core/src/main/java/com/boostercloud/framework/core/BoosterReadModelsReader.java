package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.services.FilterHelpers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.metadata.PropertyMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Port of {@code booster-read-models-reader.ts}: the read side of read models (validation, authorization,
 * {@code before} hooks, {@code findById}, {@code search} with {@code select} projections and calculated-field
 * dependencies, subscriptions). {@link #readModelSearch} and {@link #finderByIdFunction} back the user-facing
 * {@code Searcher} ({@code Booster.readModel(...)}): their signatures match {@code SearcherFunction} and
 * {@code FinderByKeyFunction} and must be kept.
 *
 * <p>Two paths, as in TypeScript:
 *
 * <ul>
 *   <li>the GraphQL path ({@link #findById}, {@link #search}, {@link #subscribe}) validates the request (version, read
 *       model, authorizer, and for {@code findById} the sequence key name) and applies the read model's
 *       {@code before} hooks;</li>
 *   <li>the programmatic path ({@link #readModelSearch}, {@link #finderByIdFunction}, used by
 *       {@code Booster.readModel(...)} and the read model store) does none of that.</li>
 * </ul>
 *
 * <p>Searches without {@code select} return schema-migrated instances of the read model class; with {@code select}
 * they return plain objects ({@code Map}) with only the selected paths, calculated fields evaluated and no
 * migration. A paginated search returns a {@link ReadModelListResult}, any other a {@code List}. Trace actions:
 * {@code READ_MODEL_FIND_BY_ID}, {@code GRAPHQL_READ_MODEL_SEARCH}, {@code READ_MODEL_SEARCH}.
 */
public class BoosterReadModelsReader {

  /** Thrown (as an {@link IllegalStateException}) by {@link #findById} when the request has no key. */
  public static final String MISSING_KEY_MESSAGE =
      "Tried to run a findById operation without providing a key. An ID is required to perform this operation.";

  private static final Pattern ARRAY_MARK = Pattern.compile(Pattern.quote("[]"));

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterReadModelsReader(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code findById(readModelRequest)}: validates (including the sequence key name), applies the {@code before}
   * hooks, then looks the key of the <b>transformed</b> request up with {@code Booster.readModel(cls).findById(id,
   * sequenceKey)} (the global configuration, as in TypeScript). A found read model is schema-migrated and returned as
   * an instance of the read model class; a list (sequenced read models) gives a one-element list with the first
   * element migrated; nothing found returns {@code null}.
   *
   * @param readModelRequest the request
   * @return the read model, or the list of read models for sequenced read models
   * @throws IllegalStateException {@value #MISSING_KEY_MESSAGE} when the transformed request has no key
   */
  public Object findById(ReadModelRequestEnvelope readModelRequest) {
    return Tracer.trace(TraceActionTypes.READ_MODEL_FIND_BY_ID.value(), BoosterReadModelsReader.class, "findById",
        Arrays.asList(readModelRequest), () -> {
          validateByIdRequest(readModelRequest);

          ReadModelMetadata readModelMetadata = config.readModels.get(readModelName(readModelRequest));
          ReadModelRequestEnvelope readModelTransformedRequest = FilterHelpers.applyReadModelRequestBeforeFunctions(
              readModelRequest, readModelMetadata.before(), readModelRequest.currentUser());

          ReadModelRequestEnvelope.Key key = readModelTransformedRequest.key();
          if (key == null) {
            throw new IllegalStateException(MISSING_KEY_MESSAGE);
          }
          Object currentReadModel = Booster.readModel(readModelMetadata.type()).findById(key.id(), key.sequenceKey());
          if (Js.isTruthy(currentReadModel)) {
            Class<?> readModelClass = readModelMetadata.type();
            String readModelName = readModelClass.getSimpleName();
            ReadModelSchemaMigrator readModelSchemaMigrator = new ReadModelSchemaMigrator(config);
            if (currentReadModel instanceof List<?> readModels) {
              List<Object> migrated = new ArrayList<>();
              if (!readModels.isEmpty()) {
                migrated.add(migratedInstance(readModelSchemaMigrator, readModelClass, readModelName, readModels.get(0)));
              }
              return migrated;
            }
            return migratedInstance(readModelSchemaMigrator, readModelClass, readModelName, currentReadModel);
          }
          return currentReadModel;
        });
  }

  /**
   * {@code search(readModelRequest)}: validates, applies the {@code before} hooks and runs
   * {@link #readModelSearch} with the class of the original request and every other parameter from the transformed
   * request.
   *
   * @param readModelRequest the request
   * @return a {@code List} or a {@code ReadModelListResult} (paginated version)
   */
  public Object search(ReadModelRequestEnvelope readModelRequest) {
    return Tracer.trace(TraceActionTypes.GRAPHQL_READ_MODEL_SEARCH.value(), BoosterReadModelsReader.class, "search",
        Arrays.asList(readModelRequest), () -> {
          validateRequest(readModelRequest);

          ReadModelMetadata readModelMetadata = config.readModels.get(readModelName(readModelRequest));
          ReadModelRequestEnvelope readModelTransformedRequest = FilterHelpers.applyReadModelRequestBeforeFunctions(
              readModelRequest, readModelMetadata.before(), readModelRequest.currentUser());
          return readModelSearch(readModelMetadata.type(), readModelTransformedRequest.filters(),
              readModelTransformedRequest.sortBy(), readModelTransformedRequest.limit(),
              readModelTransformedRequest.afterCursor(), Boolean.TRUE.equals(readModelTransformedRequest.paginatedVersion()),
              readModelTransformedRequest.select());
        });
  }

  /**
   * {@code readModelSearch(readModelClass, filters, sort, limit, afterCursor, paginatedVersion, select)}: no
   * validation, authorization or hooks. The provider receives {@code filters ?? {}}, {@code sortBy ?? {}},
   * {@code limit}, {@code afterCursor}, {@code paginatedVersion} and the selection extended with the
   * {@code dependsOn} paths of the selected calculated fields. Without {@code select} every item is schema-migrated
   * and bound to the class; with {@code select} (also an empty one) every item is the plain object of the selected
   * paths of the original selection, calculated fields included, not migrated.
   *
   * @param readModelClass the read model class
   * @param filters the filters, or {@code null}
   * @param sortBy the sort, or {@code null}
   * @param limit the limit, or {@code null}
   * @param afterCursor the cursor, or {@code null}
   * @param paginatedVersion whether a {@code ReadModelListResult} is returned
   * @param select the selected paths, or {@code null}
   * @param <T> the read model type
   * @return a {@code List<T>} or a {@code ReadModelListResult<T>} ({@code Map}s instead of {@code T} with a selection)
   */
  public <T> Object readModelSearch(Class<T> readModelClass, Map<String, Object> filters, Map<String, Object> sortBy,
      Integer limit, Object afterCursor, boolean paginatedVersion, List<String> select) {
    return Tracer.trace(TraceActionTypes.READ_MODEL_SEARCH.value(), BoosterReadModelsReader.class, "readModelSearch",
        Arrays.asList(readModelClass, filters, sortBy, limit, afterCursor, paginatedVersion, select), () -> {
          String readModelName = readModelClass.getSimpleName();

          List<String> selectWithDependencies = null;
          Map<String, List<String>> calculatedFieldsDependencies = getCalculatedFieldsDependencies(readModelClass);
          if (select != null && !calculatedFieldsDependencies.isEmpty()) {
            LinkedHashSet<String> extendedSelect = new LinkedHashSet<>(select);
            for (String field : select) {
              String topLevelField = ARRAY_MARK.matcher(field.split("\\.", -1)[0]).replaceFirst("");
              List<String> dependencies = calculatedFieldsDependencies.get(topLevelField);
              if (dependencies != null) {
                extendedSelect.addAll(dependencies);
              }
            }
            selectWithDependencies = new ArrayList<>(extendedSelect);
          }

          ReadModelListResult<Map<String, Object>> searchResult = providerSearch(readModelName, filters, sortBy, limit,
              afterCursor, paginatedVersion, selectWithDependencies != null ? selectWithDependencies : select);

          List<Object> readModels;
          if (select != null) {
            readModels = createReadModelInstancesWithCalculatedProperties(searchResult.items(), readModelClass, select);
          } else {
            readModels = new ArrayList<>();
            for (Map<String, Object> document : migrateDocuments(searchResult.items(), readModelName)) {
              readModels.add(Instances.createInstance(readModelClass, document));
            }
          }
          if (paginatedVersion) {
            return new ReadModelListResult<>(readModels, searchResult.count(), searchResult.cursor());
          }
          return readModels;
        });
  }

  /**
   * The documents behind a non-paginated {@link #readModelSearch} without selection, before they are bound to the read
   * model class: the provider search ({@code filters ?? {}}, sort {@code {}}, no limit, no cursor, not paginated, no
   * selection) followed by the schema migration of every item. Java addition used by the read model store, which
   * needs the stored {@code boosterMetadata} that a read model class may not declare; traced as
   * {@code READ_MODEL_SEARCH} like the search it replaces
   * ({@code readModelSearcher(config, cls).filter(filters).paginatedVersion(false).search()}).
   *
   * @param readModelClass the read model class
   * @param filters the filters, or {@code null}
   * @return the migrated raw read models, in provider order
   */
  public List<Map<String, Object>> readModelSearchDocuments(Class<?> readModelClass, Map<String, Object> filters) {
    Map<String, Object> sortBy = new LinkedHashMap<>();
    return Tracer.trace(TraceActionTypes.READ_MODEL_SEARCH.value(), BoosterReadModelsReader.class, "readModelSearch",
        Arrays.asList(readModelClass, filters, sortBy, null, null, false, null), () -> {
          String readModelName = readModelClass.getSimpleName();
          ReadModelListResult<Map<String, Object>> searchResult =
              providerSearch(readModelName, filters, sortBy, null, null, false, null);
          return migrateDocuments(searchResult.items(), readModelName);
        });
  }

  /**
   * {@code finderByIdFunction(readModelClass, id, sequenceKey?)}: the raw provider documents (no instantiation,
   * migration, authorization or hooks); the whole list with a sequence key, the first document (or {@code null})
   * without.
   *
   * @param readModelClass the read model class
   * @param id the id
   * @param sequenceKey the sequence key, or {@code null}
   * @param <T> the read model type
   * @return a raw document or a list of raw documents
   */
  public <T> Object finderByIdFunction(Class<T> readModelClass, UUID id, SequenceKey sequenceKey) {
    List<Map<String, Object>> readModels =
        config.provider().readModels().fetch(config, readModelClass.getSimpleName(), id, sequenceKey);
    if (sequenceKey != null) {
      return readModels;
    }
    return readModels == null || readModels.isEmpty() ? null : readModels.get(0);
  }

  /**
   * {@code subscribe(connectionID, readModelRequest, operation)}: validates, applies the {@code before} hooks and
   * stores {@code { ...transformedRequest, expirationTime: nowEpochSeconds + subscriptions.maxDurationInSeconds,
   * connectionID, operation }} through the provider.
   *
   * @param connectionID the websocket connection
   * @param readModelRequest the request
   * @param operation the GraphQL operation
   * @return the provider result ({@code null}: the provider method is {@code void})
   */
  public Object subscribe(String connectionID, ReadModelRequestEnvelope readModelRequest, GraphQLOperation operation) {
    validateRequest(readModelRequest);
    return processSubscription(connectionID, readModelRequest, operation);
  }

  /**
   * {@code unsubscribe(connectionID, subscriptionID)}: {@code provider.readModels().deleteSubscription}.
   *
   * @param connectionID the websocket connection
   * @param subscriptionID the subscription (the GraphQL operation id)
   */
  public void unsubscribe(String connectionID, String subscriptionID) {
    config.provider().readModels().deleteSubscription(config, connectionID, subscriptionID);
  }

  /**
   * {@code unsubscribeAll(connectionID)}: {@code provider.readModels().deleteAllSubscriptions}.
   *
   * @param connectionID the websocket connection
   */
  public void unsubscribeAll(String connectionID) {
    config.provider().readModels().deleteAllSubscriptions(config, connectionID);
  }

  /** {@code validateByIdRequest}: version, read model, authorizer, then the sequence key name. */
  void validateByIdRequest(ReadModelRequestEnvelope readModelByIdRequest) {
    Logger logger = Loggers.getLogger(config, "BoosterReadModelsReader#validateByIdRequest");
    logger.debug("Validating the following read model by id request: ", readModelByIdRequest);
    String readModelName = validateAndAuthorize(readModelByIdRequest);

    ReadModelRequestEnvelope.Key key = readModelByIdRequest.key();
    if (key != null && key.sequenceKey() != null
        && !Objects.equals(key.sequenceKey().name(), config.readModelSequenceKeys.get(readModelName))) {
      throw new InvalidParameterError("Could not find a sort key defined for " + readModelName + " named '"
          + key.sequenceKey().name() + "'.");
    }
  }

  /** {@code validateRequest}: version, read model, authorizer. */
  void validateRequest(ReadModelRequestEnvelope readModelRequest) {
    Logger logger = Loggers.getLogger(config, "BoosterReadModelsReader#validateRequest");
    logger.debug("Validating the following read model request: ", readModelRequest);
    validateAndAuthorize(readModelRequest);
  }

  private String validateAndAuthorize(ReadModelRequestEnvelope readModelRequest) {
    if (readModelRequest.version() == 0) {
      throw new InvalidParameterError("The required request \"version\" was not present");
    }
    String readModelName = readModelName(readModelRequest);
    ReadModelMetadata readModelMetadata = config.readModels.get(readModelName);
    if (readModelMetadata == null) {
      throw new NotFoundError("Could not find read model " + readModelName);
    }
    readModelMetadata.authorizer().authorize(readModelRequest.currentUser(), readModelRequest);
    return readModelName;
  }

  private Object processSubscription(String connectionID, ReadModelRequestEnvelope readModelRequest,
      GraphQLOperation operation) {
    Logger logger = Loggers.getLogger(config, "BoosterReadModelsReader#processSubscription");
    String readModelName = readModelName(readModelRequest);
    logger.info("Processing subscription of connection '" + connectionID + "' to read model '" + readModelName
        + "' with the following data: ", readModelRequest);
    ReadModelMetadata readModelMetadata = config.readModels.get(readModelName);

    ReadModelRequestEnvelope newReadModelRequest = FilterHelpers.applyReadModelRequestBeforeFunctions(readModelRequest,
        readModelMetadata.before(), readModelRequest.currentUser());

    long nowEpoch = Dates.nowEpochSeconds();
    SubscriptionEnvelope subscription = SubscriptionEnvelope.of(newReadModelRequest,
        nowEpoch + config.subscriptions.maxDurationInSeconds, connectionID, operation);
    config.provider().readModels().subscribe(config, subscription);
    return null;
  }

  /** The read model name of a request: its class's simple name ({@code readModelRequest.class.name}), else its {@code className}. */
  static String readModelName(ReadModelRequestEnvelope readModelRequest) {
    return readModelRequest.type() != null ? readModelRequest.type().getSimpleName() : readModelRequest.className();
  }

  private ReadModelListResult<Map<String, Object>> providerSearch(String readModelName, Map<String, Object> filters,
      Map<String, Object> sortBy, Integer limit, Object afterCursor, boolean paginatedVersion, List<String> select) {
    ReadModelListResult<Map<String, Object>> searchResult = config.provider().readModels().search(config, readModelName,
        filters != null ? filters : new LinkedHashMap<>(), sortBy != null ? sortBy : new LinkedHashMap<>(), limit,
        afterCursor, paginatedVersion, select);
    return searchResult != null ? searchResult : ReadModelListResult.of(List.of());
  }

  /** {@code migrateReadModels}: every item migrated concurrently ({@code Promise.all}), in order. */
  private List<Map<String, Object>> migrateDocuments(List<Map<String, Object>> readModels, String readModelName) {
    ReadModelSchemaMigrator readModelSchemaMigrator = new ReadModelSchemaMigrator(config);
    List<Callable<Map<String, Object>>> migrations = new ArrayList<>();
    for (Map<String, Object> readModel : readModels) {
      migrations.add(() -> readModelSchemaMigrator.migrate(readModel, readModelName));
    }
    return Promises.runAll(migrations);
  }

  private static List<Object> createReadModelInstancesWithCalculatedProperties(List<Map<String, Object>> searchResult,
      Class<?> readModelClass, List<String> propertiesToInclude) {
    List<Callable<Object>> instances = new ArrayList<>();
    for (Map<String, Object> raw : searchResult) {
      instances.add(() -> Instances.createInstanceWithCalculatedProperties(readModelClass, raw, propertiesToInclude));
    }
    return Promises.runAll(instances);
  }

  private static Object migratedInstance(ReadModelSchemaMigrator migrator, Class<?> readModelClass, String readModelName,
      Object readModel) {
    if (readModel == null) {
      return null;
    }
    return Instances.createInstance(readModelClass, migrator.migrate(toDocument(readModel), readModelName));
  }

  /** {@code getCalculatedFieldsDependencies}: property name to its {@code dependsOn} paths (empty when unregistered). */
  private Map<String, List<String>> getCalculatedFieldsDependencies(Class<?> readModelClass) {
    ReadModelMetadata readModelMetadata = config.readModels.get(readModelClass.getSimpleName());
    Map<String, List<String>> dependenciesMap = new LinkedHashMap<>();
    if (readModelMetadata != null) {
      for (PropertyMetadata property : readModelMetadata.properties()) {
        dependenciesMap.put(property.name(), property.dependencies());
      }
    }
    return dependenciesMap;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toDocument(Object readModel) {
    return readModel instanceof Map<?, ?> map ? (Map<String, Object>) map : BoosterJson.toRawMap(readModel);
  }
}
