package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.provider.local.services.ReadModelRegistry;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.provider.local.store.NedbError;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The read models part of the provider: fetch by id, store with optimistic concurrency (and subscription
 * notification), search and delete. Port of {@code library/read-model-adapter.ts}.
 */
public final class ReadModelAdapter {

  private ReadModelAdapter() {}

  /**
   * {@code rawReadModelEventsToEnvelopes(config, rawEvents)}: the change notifications handed to
   * {@code boosterNotifySubscribers} (envelope records are kept, raw {@code { typeName, value }} maps converted).
   *
   * @param config the configuration
   * @param rawEvents a list of envelopes
   * @return the envelopes
   */
  public static List<ReadModelEnvelope> rawReadModelEventsToEnvelopes(BoosterConfig config, Object rawEvents) {
    if (!(rawEvents instanceof List<?> events)) {
      throw new IllegalArgumentException("Expected a list of read model envelopes but got " + rawEvents);
    }
    List<ReadModelEnvelope> envelopes = new ArrayList<>(events.size());
    for (Object event : events) {
      envelopes.add(event instanceof ReadModelEnvelope envelope
          ? envelope
          : BoosterJson.convert(event, ReadModelEnvelope.class));
    }
    return envelopes;
  }

  /**
   * {@code fetchReadModel(db, config, readModelName, readModelID)}: the read model with that id. The sequence key of
   * the provider contract is ignored, as in TypeScript.
   *
   * @param db the registry
   * @param config the configuration
   * @param readModelName the read model name
   * @param readModelID the id
   * @return a one-element list with the read model, or an empty list when it does not exist (TypeScript returns
   *     {@code [undefined]})
   */
  public static List<Map<String, Object>> fetchReadModel(ReadModelRegistry db, BoosterConfig config,
      String readModelName, UUID readModelID) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#fetchReadModel");
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("typeName", readModelName);
    query.put("value.id", readModelID == null ? null : readModelID.value());
    List<Map<String, Object>> response = db.query(query);
    Map<String, Object> item = response == null || response.isEmpty() ? null : response.get(0);
    if (item == null) {
      logger.debug("Read model " + readModelName + " with ID " + readModelID + " not found");
      return new ArrayList<>();
    }
    logger.debug("Loaded read model " + readModelName + " with ID " + readModelID + " with result:", item.get("value"));
    List<Map<String, Object>> result = new ArrayList<>(1);
    result.add(asMap(item.get("value")));
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : BoosterJson.toRawMap(value);
  }

  /**
   * {@code storeReadModel(graphQLService, db, config, readModelName, readModel, expectedCurrentVersion)}: stores the
   * read model; a unique or optimistic-concurrency violation becomes an
   * {@link OptimisticConcurrencyUnexpectedVersionError} (which core retries). When subscriptions are enabled the
   * subscribers are then notified, synchronously; a notification failure is only logged.
   *
   * @param graphQLService the GraphQL service (subscription notifications)
   * @param db the registry
   * @param config the configuration
   * @param readModelName the read model name
   * @param readModel the read model document
   * @param expectedCurrentVersion the version the caller read
   */
  public static void storeReadModel(GraphQLService graphQLService, ReadModelRegistry db, BoosterConfig config,
      String readModelName, Map<String, Object> readModel, Long expectedCurrentVersion) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#storeReadModel");
    logger.debug("Storing readModel " + Js.stringify(readModel));
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("typeName", readModelName);
      envelope.put("value", readModel);
      db.store(envelope, expectedCurrentVersion);
    } catch (NedbError error) {
      if (ReadModelRegistry.UNIQUE_VIOLATED_ERROR_TYPE.equals(error.errorType())) {
        logger.warn("Unique violated storing ReadModel " + Js.stringify(readModel) + " and expectedCurrentVersion "
            + (expectedCurrentVersion == null ? "undefined" : expectedCurrentVersion));
        throw new OptimisticConcurrencyUnexpectedVersionError(error.getMessage());
      }
      throw error;
    }
    logger.debug("Read model stored");
    if (config.enableSubscriptions) {
      try {
        graphQLService.handleNotificationSubscription(List.of(new ReadModelEnvelope(readModelName, readModel)));
        logger.debug("Read model change notified");
      } catch (RuntimeException e) {
        logger.error("Error notifying subscription", readModel);
      }
    }
  }

  /**
   * {@code searchReadModel(db, config, readModelName, filters, sortBy?, limit?, afterCursor?, paginatedVersion = false,
   * select?)}: translates the filter ({@link SearcherAdapter}), adds {@code typeName}, and skips
   * {@code parseInt(afterCursor.id)} results (offset pagination). The paginated shape is
   * {@code { items, count: items.length, cursor: { id: String((limit || 1) + offset) } }}; otherwise only
   * {@code items} is set.
   *
   * @param db the registry
   * @param config the configuration
   * @param readModelName the read model name
   * @param filters the Booster filter ({@code null} means no filter)
   * @param sortBy the Booster sort, or {@code null}
   * @param limit the maximum number of results, or {@code null}
   * @param afterCursor the cursor of the previous page ({@code { id }}), or {@code null}
   * @param paginatedVersion whether the paginated shape was requested
   * @param select projection paths, or {@code null}
   * @return the read models
   */
  public static ReadModelListResult<Map<String, Object>> searchReadModel(ReadModelRegistry db, BoosterConfig config,
      String readModelName, Map<String, Object> filters, Map<String, Object> sortBy, Integer limit, Object afterCursor,
      Boolean paginatedVersion, List<String> select) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#searchReadModel");
    logger.debug("Converting filter to query");
    Map<String, Object> queryFor = SearcherAdapter.queryRecordFor(filters == null ? Map.of() : filters);
    Map<String, Object> query = new LinkedHashMap<>(queryFor);
    query.put("typeName", readModelName);
    logger.debug("Got query ", query);
    Object cursorId = Js.get(afterCursor, "id");
    double skipId = Js.truthy(cursorId) ? Js.parseInt(cursorId) : 0;
    List<Map<String, Object>> result = db.query(query, sortBy, skipId, limit, select);
    logger.debug("Search result: ", result);
    List<Map<String, Object>> items = new ArrayList<>();
    if (result != null) {
      for (Map<String, Object> envelope : result) {
        items.add(asMap(envelope.get("value")));
      }
    }
    if (Boolean.TRUE.equals(paginatedVersion)) {
      Map<String, String> cursor = new LinkedHashMap<>();
      cursor.put("id", Js.toJsString((Js.truthy(limit) ? limit : 1) + skipId));
      return new ReadModelListResult<>(items, items.size(), cursor);
    }
    return ReadModelListResult.of(items);
  }

  /**
   * {@code deleteReadModel(db, config, readModelName, readModel)}: removes the read model with
   * {@code readModel.id}; every registry error is swallowed (logged as "not found"). A {@code null} read model is a
   * no-op (the Java provider contract; TypeScript fails with a {@code TypeError}).
   *
   * @param db the registry
   * @param config the configuration
   * @param readModelName the read model name
   * @param readModel the read model document, or {@code null}
   */
  public static void deleteReadModel(ReadModelRegistry db, BoosterConfig config, String readModelName,
      Map<String, Object> readModel) {
    Logger logger = Loggers.getLogger(config, "read-model-adapter#deleteReadModel");
    if (readModel == null) {
      logger.debug("No read model to delete. Name=" + readModelName);
      return;
    }
    Object id = Js.get(readModel, "id");
    logger.debug("Entering to Read model deleted. ID=" + Js.toJsString(id) + ".Name=" + readModelName);
    try {
      db.deleteById(id, readModelName);
      logger.debug("Read model deleted. " + readModelName + " ID = " + Js.toJsString(id));
    } catch (RuntimeException e) {
      logger.warn("Read model to delete " + readModelName + " ID = " + Js.toJsString(id) + " not found");
    }
  }
}
