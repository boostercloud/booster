package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants;
import com.boostercloud.framework.provider.azure.Constants.EventsStoreAttributes;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.types.AzureFunctionTypes;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code library/events-adapter.ts}: change feed payloads to envelopes, reading the events of an entity since
 * a timestamp, the latest snapshot, storing snapshots and the dispatched-event markers.
 */
public final class EventsAdapter {

  /** {@code originOfTime = new Date(0).toISOString()}. */
  public static final String ORIGIN_OF_TIME = "1970-01-01T00:00:00.000Z";

  private EventsAdapter() {}

  /**
   * TypeScript's {@code rawEventsToEnvelopes}: the documents of a Cosmos DB change feed input (or a plain array),
   * untouched (system properties, snapshots and soft-deleted events included).
   *
   * @param rawInput an {@code AzureCosmosDBFunctionInput} (record or map) or a list
   * @return the documents (same list instance)
   * @throws IllegalStateException for any other input
   */
  @SuppressWarnings("unchecked")
  public static List<Object> rawEventsToDocuments(Object rawInput) {
    if (AzureFunctionTypes.isCosmosDBFunctionInput(rawInput)) {
      return (List<Object>) get(rawInput, "documents");
    }
    // Fallback for direct array input (used in some test scenarios)
    if (rawInput instanceof List<?> list) {
      return (List<Object>) list;
    }
    throw new IllegalStateException(
        "Invalid input type for rawEventsToEnvelopes: expected AzureCosmosDBFunctionInput or Array");
  }

  /**
   * {@code rawEventsToEnvelopes} as the provider SPI needs it: the documents of {@link #rawEventsToDocuments(Object)}
   * whose {@code kind} is {@code event}, as envelopes. Java envelopes have a constant {@code kind()}, so core's
   * {@code kind == 'event'} filter could not tell snapshots apart; the provider applies it instead. Soft-deleted
   * events are kept (they carry {@code deletedAt}, which core filters).
   *
   * @param rawInput the change feed input
   * @return the event envelopes
   */
  public static List<EventEnvelope> rawEventsToEnvelopes(Object rawInput) {
    List<Object> events = new ArrayList<>();
    for (Object document : rawEventsToDocuments(rawInput)) {
      if (EventDocuments.isEvent(document)) {
        events.add(document);
      }
    }
    return EventDocuments.toEventEnvelopes(events);
  }

  /**
   * {@code readEntityEventsSince}: the non-deleted events of an entity created strictly after {@code since}
   * ({@code 1970-01-01T00:00:00.000Z} when {@code since} is absent or empty), oldest first. The query is the
   * TypeScript one; it runs as a single-partition query because it pins the partition key.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @param since ISO timestamp, or {@code null}
   * @return the events
   */
  public static List<EventEnvelope> readEntityEventsSince(
      CosmosClient cosmosDb, BoosterConfig config, String entityTypeName, Object entityID, String since) {
    Logger logger = Loggers.getLogger(config, "events-adapter#readEntityEventsSince");
    String fromTime = since != null && !since.isEmpty() ? since : ORIGIN_OF_TIME;
    String partitionKey = PartitionKeys.partitionKeyForEvent(entityTypeName, entityID);
    SqlQuerySpec querySpec = new SqlQuerySpec(
        "SELECT * FROM c WHERE c[\"" + EventsStoreAttributes.PARTITION_KEY + "\"] = @partitionKey "
            + "AND c[\"" + EventsStoreAttributes.SORT_KEY + "\"] > @fromTime "
            + "AND NOT IS_DEFINED(c[\"deletedAt\"]) "
            + "ORDER BY c[\"" + EventsStoreAttributes.SORT_KEY + "\"] ASC",
        List.of(SqlParameter.of("@partitionKey", partitionKey), SqlParameter.of("@fromTime", fromTime)));
    List<Object> resources = eventsContainer(cosmosDb, config).items()
        .query(querySpec, FeedOptions.forPartition(partitionKey))
        .fetchAll()
        .resources();
    logger.debug("Loaded events for entity " + entityTypeName + " with ID " + entityID + " with result:", resources);
    return EventDocuments.toEventEnvelopes(resources);
  }

  /**
   * {@code readEntityLatestSnapshot}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @return the most recent snapshot, if any
   */
  public static Optional<EntitySnapshotEnvelope> readEntityLatestSnapshot(
      CosmosClient cosmosDb, BoosterConfig config, String entityTypeName, Object entityID) {
    Logger logger = Loggers.getLogger(config, "events-adapter#readEntityLatestSnapshot");
    String partitionKey = PartitionKeys.partitionKeyForSnapshot(entityTypeName, entityID);
    SqlQuerySpec querySpec = new SqlQuerySpec(
        "SELECT * FROM c WHERE c[\"" + EventsStoreAttributes.PARTITION_KEY + "\"] = @partitionKey "
            + "ORDER BY c[\"" + EventsStoreAttributes.SORT_KEY + "\"] DESC OFFSET 0 LIMIT 1",
        List.of(SqlParameter.of("@partitionKey", partitionKey)));
    List<Object> resources = eventsContainer(cosmosDb, config).items()
        .query(querySpec, FeedOptions.forPartition(partitionKey))
        .fetchAll()
        .resources();
    Object snapshot = resources.isEmpty() ? null : resources.getFirst();
    if (snapshot != null) {
      logger.debug("[EventsAdapter#readEntityLatestSnapshot] Snapshot found for entity " + entityTypeName + " with ID "
          + entityID + ":", snapshot);
      return Optional.of(EventDocuments.toSnapshotEnvelopes(List.of(snapshot)).getFirst());
    }
    logger.debug("[EventsAdapter#readEntityLatestSnapshot] No snapshot found for entity " + entityTypeName + " with ID "
        + entityID + ".");
    return Optional.empty();
  }

  /**
   * {@code storeSnapshot}. Keeps the TypeScript duplicate guard, a query on {@code c.partitionKey}/{@code c.sortKey}
   * that never matches stored documents (quirk Q1), so duplicate snapshots are simply created.
   *
   * @param cosmosDb the client
   * @param snapshotEnvelope the snapshot
   * @param config the configuration
   * @return the stored snapshot ({@code createdAt = snapshottedEventCreatedAt}, {@code persistedAt = now})
   */
  public static EntitySnapshotEnvelope storeSnapshot(
      CosmosClient cosmosDb, NonPersistedEntitySnapshotEnvelope snapshotEnvelope, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-adapter#storeSnapshot");
    logger.debug("Storing snapshot with snapshotEnvelope:", snapshotEnvelope);

    String partitionKey =
        PartitionKeys.partitionKeyForSnapshot(snapshotEnvelope.entityTypeName(), snapshotEnvelope.entityID());
    // The sort key of the snapshot matches the sort key of the last event that generated it.
    String sortKey = snapshotEnvelope.snapshottedEventCreatedAt();
    CosmosContainer container = eventsContainer(cosmosDb, config);

    List<Object> resources = container.items()
        .query(new SqlQuerySpec(
            "SELECT * FROM c WHERE c.partitionKey = @partitionKey AND c.sortKey = @sortKey",
            List.of(SqlParameter.of("@partitionKey", partitionKey), SqlParameter.of("@sortKey", sortKey))))
        .fetchAll()
        .resources();
    if (!resources.isEmpty()) {
      throw new IllegalStateException("Snapshot already exists. skipping...");
    }

    EntitySnapshotEnvelope persistableEntitySnapshot = snapshotEnvelope.persisted(Dates.nowIso());
    Map<String, Object> document = new LinkedHashMap<>(BoosterJson.toRawMap(persistableEntitySnapshot));
    document.put(EventsStoreAttributes.PARTITION_KEY, partitionKey);
    document.put(EventsStoreAttributes.SORT_KEY, sortKey);
    container.items().create(document);
    logger.debug("Snapshot stored", snapshotEnvelope);
    return persistableEntitySnapshot;
  }

  /**
   * {@code storeDispatchedEvent}: creates {@code { eventId }} in the dispatched-events container (unique key and
   * partition key {@code /eventId}, TTL {@code config.dispatchedEventsTtl}).
   *
   * @param cosmosDb the client
   * @param eventEnvelope the dispatched event (its database {@code id})
   * @param config the configuration
   * @return {@code true} when stored, {@code false} when the event was already dispatched (409)
   */
  public static boolean storeDispatchedEvent(CosmosClient cosmosDb, EventEnvelope eventEnvelope, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-adapter#storeDispatchedEvent");
    logger.debug("[EventsAdapter#storeDispatchedEvent] Storing EventEnvelope for event with ID: ", eventEnvelope.id());
    Map<String, Object> marker = new LinkedHashMap<>();
    if (eventEnvelope.id() != null) {
      marker.put("eventId", eventEnvelope.id());
    }
    try {
      cosmosDb.database(config.resourceNames().applicationStack())
          .container(config.resourceNames().dispatchedEventsStore())
          .items()
          .create(marker);
      return true;
    } catch (RuntimeException e) {
      if (e instanceof CosmosException cosmosError && cosmosError.code() == Constants.AZURE_CONFLICT_ERROR_CODE) {
        // If an item with the same ID already exists in the container, it will return a 409 status code.
        logger.debug("[EventsAdapter#storeDispatchedEvent] Event has already been dispatched", eventEnvelope.id());
        return false;
      }
      logger.error("[EventsAdapter#storeDispatchedEvent] Error storing dispatched event", e);
      throw e;
    }
  }

  private static CosmosContainer eventsContainer(CosmosClient cosmosDb, BoosterConfig config) {
    return cosmosDb.database(config.resourceNames().applicationStack()).container(config.resourceNames().eventsStore());
  }
}
