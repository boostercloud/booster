package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItems;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port of {@code library/read-model-adapter.ts}: fetch, insert, update (etag optimistic concurrency) and delete read
 * models, and change feed payloads to read model envelopes. Read models are raw documents
 * ({@code Map<String, Object>}) with a {@code boosterMetadata} key.
 */
public final class ReadModelAdapter {

  private static final Set<String> SYSTEM_PROPERTIES = Set.of("_rid", "_self", "_st", "_etag", "_lsn", "_ts");

  private ReadModelAdapter() {}

  /**
   * {@code fetchReadModel}: reads the document whose id and partition key are both {@code readModelID}. The result
   * keeps the Cosmos system properties and gets {@code boosterMetadata.optimisticConcurrencyValue = _etag}. A missing
   * document gives an empty list (TypeScript returns {@code [undefined]}; the Java SPI defines "not found" as empty).
   *
   * @param db the client
   * @param config the configuration
   * @param readModelName the read model class name
   * @param readModelID the id
   * @return the document, or nothing
   */
  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> fetchReadModel(
      CosmosClient db, BoosterConfig config, String readModelName, Object readModelID) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#fetchReadModel");
    String id = String.valueOf(readModelID);
    ItemResponse response = db.database(config.resourceNames().applicationStack())
        .container(config.resourceNames().forReadModel(readModelName))
        .item(id, id)
        .read();
    Map<String, Object> resource = response == null ? null : response.resource();
    logger.debug("[ReadModelAdapter#fetchReadModel] Loaded read model " + readModelName + " with ID " + readModelID
        + " with result:", resource);
    List<Map<String, Object>> result = new ArrayList<>();
    if (resource == null) {
      return result;
    }
    Map<String, Object> readModel = new LinkedHashMap<>(resource);
    Map<String, Object> metadata = resource.get("boosterMetadata") instanceof Map<?, ?> existing
        ? new LinkedHashMap<>((Map<String, Object>) existing)
        : new LinkedHashMap<>();
    if (resource.containsKey("_etag")) {
      metadata.put("optimisticConcurrencyValue", resource.get("_etag"));
    } else {
      metadata.remove("optimisticConcurrencyValue");
    }
    readModel.put("boosterMetadata", metadata);
    result.add(readModel);
    return result;
  }

  /**
   * {@code storeReadModel}: inserts when {@code boosterMetadata.version ?? 1} is 1, upserts with If-Match otherwise.
   *
   * @param db the client
   * @param config the configuration
   * @param readModelName the read model class name
   * @param readModel the document
   * @throws OptimisticConcurrencyUnexpectedVersionError on a 409 insert or a 412 update
   */
  public static void storeReadModel(
      CosmosClient db, BoosterConfig config, String readModelName, Map<String, Object> readModel) {
    Object version = get(get(readModel, "boosterMetadata"), "version");
    boolean isFirstVersion = version == null || (version instanceof Number number && number.doubleValue() == 1);
    if (isFirstVersion) {
      insertReadModel(readModel, db, config, readModelName);
      return;
    }
    updateReadModel(readModel, db, config, readModelName);
  }

  private static void insertReadModel(
      Map<String, Object> readModel, CosmosClient db, BoosterConfig config, String readModelName) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#insertReadModel");
    try {
      Map<String, Object> itemModel = new LinkedHashMap<>(readModel);
      Object id = readModel.get("id");
      if (id == null) {
        itemModel.remove("id");
      } else {
        itemModel.put("id", id.toString());
      }
      ItemResponse response = items(db, config, readModelName).create(itemModel);
      logger.debug("Read model " + readModelName + " inserted with id " + readModel.get("id") + " and metadata "
          + JsValues.stringify(response == null ? null : get(response.resource(), "boosterMetadata")));
    } catch (RuntimeException error) {
      // In case of conflict (the id is taken by an existing resource) core must retry the projection
      if (error instanceof CosmosException cosmosError
          && cosmosError.code() == Constants.AZURE_CONFLICT_ERROR_CODE) {
        logger.warn("Read model " + readModelName + " insert failed with a conflict failure with id "
            + readModel.get("id") + " and metadata " + JsValues.stringify(readModel.get("boosterMetadata")));
        throw new OptimisticConcurrencyUnexpectedVersionError(error.getMessage());
      }
      logger.error("[ReadModelAdapter#insertReadModel] Read model insert failed without a conflict failure", error);
      throw error;
    }
  }

  private static void updateReadModel(
      Map<String, Object> readModel, CosmosClient db, BoosterConfig config, String readModelName) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#updateReadModel");
    // upsert only occurs if the etag we are sending matches the etag on the server
    Object etag = get(get(readModel, "boosterMetadata"), "optimisticConcurrencyValue");
    RequestOptions options = RequestOptions.withAccessCondition(
        RequestOptions.AccessCondition.ifMatch(JsValues.truthy(etag) ? JsValues.jsString(etag) : "*"));
    try {
      ItemResponse response = items(db, config, readModelName).upsert(readModel, options);
      logger.debug("Read model " + readModelName + " updated with id " + readModel.get("id") + " and metadata "
          + JsValues.stringify(response == null ? null : get(response.resource(), "boosterMetadata")));
    } catch (RuntimeException error) {
      // If there is a precondition failure then core must retry the projection
      if (error instanceof CosmosException cosmosError
          && cosmosError.code() == Constants.AZURE_PRECONDITION_FAILED_ERROR) {
        logger.warn("Read model " + readModelName + " update failed with a pre-condition failure with id "
            + readModel.get("id") + " and metadata " + JsValues.stringify(readModel.get("boosterMetadata")));
        throw new OptimisticConcurrencyUnexpectedVersionError(error.getMessage());
      }
      logger.error("[ReadModelAdapter#updateReadModel] Read model update failed without a pre-condition failure");
      throw error;
    }
  }

  /**
   * {@code deleteReadModel}: deletes the document; every error is logged as "not found" and swallowed. A {@code null}
   * document is a no-op (the SPI contract; TypeScript would throw a {@code TypeError}).
   *
   * @param db the client
   * @param config the configuration
   * @param readModelName the read model class name
   * @param readModel the document
   */
  public static void deleteReadModel(
      CosmosClient db, BoosterConfig config, String readModelName, Map<String, Object> readModel) {
    if (readModel == null) {
      return;
    }
    Logger logger = Loggers.getLogger(config, "read-model-adapter#deleteReadModel");
    Object id = readModel.get("id");
    logger.debug("Entering to Read model deleted. " + readModelName + " ID = " + id);
    try {
      String key = String.valueOf(id);
      db.database(config.resourceNames().applicationStack())
          .container(config.resourceNames().forReadModel(readModelName))
          .item(key, key)
          .delete();
      logger.debug("Read model deleted. " + readModelName + " ID = " + id);
    } catch (RuntimeException e) {
      logger.warn("Read model to delete " + readModelName + " ID = " + id + " not found");
    }
  }

  /**
   * {@code rawReadModelEventsToEnvelopes}: the documents of a subscriptions-notifier input without
   * {@code _rid, _self, _st, _etag, _lsn, _ts}, typed with the input's {@code typeName}; anything else gives
   * {@code []} and a warning.
   *
   * @param config the configuration
   * @param rawEvents the change feed input
   * @return the read model envelopes
   */
  @SuppressWarnings("unchecked")
  public static List<ReadModelEnvelope> rawReadModelEventsToEnvelopes(BoosterConfig config, Object rawEvents) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#rawReadModelEventsToEnvelopes");
    logger.debug("Parsing raw read models " + JsValues.stringify(rawEvents));

    if (SubscriptionModel.isSubscriptionNotifierInput(rawEvents)) {
      List<Object> documents = (List<Object>) get(rawEvents, "documents");
      Object typeName = get(rawEvents, "typeName");
      List<ReadModelEnvelope> envelopes = new ArrayList<>();
      for (Object rawEvent : documents) {
        Map<String, Object> rest = new LinkedHashMap<>();
        if (BoosterJson.toRaw(rawEvent) instanceof Map<?, ?> document) {
          document.forEach((key, value) -> {
            if (!SYSTEM_PROPERTIES.contains(String.valueOf(key))) {
              rest.put(String.valueOf(key), value);
            }
          });
        }
        envelopes.add(new ReadModelEnvelope(typeName == null ? null : typeName.toString(), rest));
      }
      return envelopes;
    }

    logger.warn("Unexpected events to be parsed " + JsValues.stringify(rawEvents));
    return new ArrayList<>();
  }

  private static CosmosItems items(CosmosClient db, BoosterConfig config, String readModelName) {
    return db.database(config.resourceNames().applicationStack())
        .container(config.resourceNames().forReadModel(readModelName))
        .items();
  }
}
