package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants.EventsStoreAttributes;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.helpers.QueryHelper;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code library/event-delete-adapter.ts}: finds deletable events and snapshots, soft-deletes events (full
 * replace with {@code value: {}} and {@code deletedAt}) and hard-deletes snapshots.
 */
public final class EventDeleteAdapter {

  private EventDeleteAdapter() {}

  /**
   * {@code findDeletableEvent}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param parameters the event identification
   * @return the matching, not yet deleted events (with their database {@code id})
   */
  @SuppressWarnings("unchecked")
  public static List<EventEnvelope> findDeletableEvent(
      CosmosClient cosmosDb, BoosterConfig config, EventDeleteParameters parameters) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#findDeletableEvent");
    String stringifyParameters = JsValues.stringify(parameters);
    logger.debug("Initiating a deletable event search for " + stringifyParameters);

    String eventStore = config.resourceNames().eventsStore();
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(EventsStoreAttributes.PARTITION_KEY,
        Map.of("eq", PartitionKeys.partitionKey(parameters.entityTypeName(), parameters.entityID(), "event")));
    filter.put("createdAt", eqOrUndefined(parameters.createdAt()));
    filter.put("kind", Map.of("eq", "event"));
    filter.put("deletedAt", Map.of("isDefined", false));
    List<Object> result = (List<Object>) QueryHelper.search(cosmosDb, config, eventStore, filter);
    logger.debug("Finished deletable event search for " + stringifyParameters);
    return EventDocuments.toEventEnvelopes(result);
  }

  /**
   * {@code findDeletableSnapshot} (note the different filter key order: kind before createdAt).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param parameters the snapshot identification
   * @return the matching snapshots
   */
  @SuppressWarnings("unchecked")
  public static List<EntitySnapshotEnvelope> findDeletableSnapshot(
      CosmosClient cosmosDb, BoosterConfig config, SnapshotDeleteParameters parameters) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#findDeletableSnapshot");
    String stringifyParameters = JsValues.stringify(parameters);
    logger.debug("Initiating a deletable snapshot search for " + stringifyParameters);

    String eventStore = config.resourceNames().eventsStore();
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(EventsStoreAttributes.PARTITION_KEY,
        Map.of("eq", PartitionKeys.partitionKey(parameters.entityTypeName(), parameters.entityID(), "snapshot")));
    filter.put("kind", Map.of("eq", "snapshot"));
    filter.put("createdAt", eqOrUndefined(parameters.createdAt()));
    filter.put("deletedAt", Map.of("isDefined", false));
    List<Object> result = (List<Object>) QueryHelper.search(cosmosDb, config, eventStore, filter);
    logger.debug("Finished deletable snapshot search for " + stringifyParameters);
    return EventDocuments.toSnapshotEnvelopes(result);
  }

  /**
   * {@code deleteEvent}: for each event, sequentially, replaces the stored document with
   * {@code { ...document, deletedAt: now, value: {} }}. The document is the one {@link #findDeletableEvent} read
   * (system properties and {@code boosterMetadata} included, as TypeScript sends back), or, for envelopes this
   * provider did not return, the envelope plus its partition key attribute.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param events the events to delete
   */
  public static void deleteEvent(CosmosClient cosmosDb, BoosterConfig config, List<EventEnvelope> events) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#deleteEvent");
    String stringifyParameters = JsValues.stringify(events);
    logger.debug("Initiating an event delete for " + stringifyParameters);

    String eventStore = config.resourceNames().eventsStore();
    if (events == null || events.isEmpty()) {
      logger.warn("Could not find events to delete");
      return;
    }
    for (EventEnvelope event : events) {
      String partitionKey = PartitionKeys.partitionKey(event.entityTypeName(), event.entityID(), "event");
      Map<String, Object> newEvent = buildNewEvent(event, partitionKey);
      QueryHelper.replaceOrDeleteItem(cosmosDb, eventStore, config, event.id(), partitionKey, newEvent);
    }
    logger.debug("Finished event delete for " + stringifyParameters);
  }

  /**
   * {@code deleteSnapshot}: deletes each snapshot document, sequentially.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param snapshots the snapshots to delete
   */
  public static void deleteSnapshot(CosmosClient cosmosDb, BoosterConfig config, List<EntitySnapshotEnvelope> snapshots) {
    Logger logger = Loggers.getLogger(config, "events-delete-adapter#deleteSnapshot");
    String stringifyParameters = JsValues.stringify(snapshots);
    logger.debug("Initiating a snapshot delete for " + stringifyParameters);

    String eventStore = config.resourceNames().eventsStore();
    if (snapshots == null || snapshots.isEmpty()) {
      logger.warn("Could not find snapshot to delete");
      return;
    }
    for (EntitySnapshotEnvelope snapshot : snapshots) {
      String partitionKey = PartitionKeys.partitionKey(snapshot.entityTypeName(), snapshot.entityID(), "snapshot");
      QueryHelper.replaceOrDeleteItem(cosmosDb, eventStore, config, snapshot.id(), partitionKey, null);
    }
    logger.debug("Finished snapshot delete for " + stringifyParameters);
  }

  private static Map<String, Object> buildNewEvent(EventEnvelope existingEvent, String partitionKey) {
    Map<String, Object> source = EventDocuments.documentFor(existingEvent);
    Map<String, Object> newEvent;
    if (source != null) {
      newEvent = new LinkedHashMap<>(source);
    } else {
      newEvent = new LinkedHashMap<>(BoosterJson.toRawMap(existingEvent));
      newEvent.put(EventsStoreAttributes.PARTITION_KEY, partitionKey);
    }
    newEvent.put("deletedAt", Dates.nowIso());
    newEvent.put("value", new LinkedHashMap<>());
    return newEvent;
  }

  private static Map<String, Object> eqOrUndefined(Object value) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("eq", value);
    return operation;
  }
}
