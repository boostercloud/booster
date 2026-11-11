package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants.EventsStoreAttributes;
import com.boostercloud.framework.provider.azure.cosmos.BatchResponse;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.OperationInput;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code library/events-store-adapter.ts}: persists events in the events container, in transactional batches
 * per partition key (default) or one by one ({@code config.azureConfiguration.enableEventBatching = false}).
 */
public final class EventsStoreAdapter {

  private EventsStoreAdapter() {}

  /**
   * {@code storeEvents}. The batch path returns the envelopes grouped by partition key (for {@code [A1, B1, A2]} it
   * returns {@code [A1, A2, B1]}); returned envelopes carry {@code createdAt} but no {@code id} (the generated id only
   * lives in the stored document), as in TypeScript.
   *
   * @param cosmosDb the client
   * @param eventEnvelopes the events
   * @param config the configuration
   * @return the stored events
   */
  public static List<EventEnvelope> storeEvents(
      CosmosClient cosmosDb, List<NonPersistedEventEnvelope> eventEnvelopes, BoosterConfig config) {
    if (config.azureConfiguration.enableEventBatching) {
      return storeEventsInBatch(cosmosDb, eventEnvelopes, config);
    }
    Logger logger = Loggers.getLogger(config, "store-events-adapter#storeEvents");
    logger.debug("Storing EventEnvelopes with eventEnvelopes:", eventEnvelopes);

    List<EventEnvelope> persistableEvents = new ArrayList<>();
    for (NonPersistedEventEnvelope eventEnvelope : eventEnvelopes) {
      EventEnvelope persistableEvent = eventEnvelope.persisted(Dates.nowIso());
      cosmosDb.database(config.resourceNames().applicationStack())
          .container(config.resourceNames().eventsStore())
          .items()
          .create(storableResource(config, persistableEvent));
      persistableEvents.add(persistableEvent);
    }
    logger.debug("EventEnvelopes stored:");
    return persistableEvents;
  }

  /**
   * {@code storeEventsInBatch}: one transactional batch per chunk of {@code config.azureConfiguration.cosmos.batchSize}
   * events of the same partition key, sequentially. A failed batch (status other than 200) throws
   * {@code Error <substatus> storing events: <JSON of the response>}; earlier chunks stay stored.
   */
  private static List<EventEnvelope> storeEventsInBatch(
      CosmosClient cosmosDb, List<NonPersistedEventEnvelope> eventEnvelopes, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "store-events-adapter#storeEventsInBatch");
    logger.debug("Storing EventEnvelopes with eventEnvelopes:", eventEnvelopes);
    List<EventEnvelope> envelopesWithCreatedAt = new ArrayList<>();

    Map<String, List<NonPersistedEventEnvelope>> eventsPerPartitionKey = new LinkedHashMap<>();
    for (NonPersistedEventEnvelope event : eventEnvelopes) {
      eventsPerPartitionKey
          .computeIfAbsent(PartitionKeys.partitionKeyForEvent(event.entityTypeName(), event.entityID()),
              ignored -> new ArrayList<>())
          .add(event);
    }
    for (Map.Entry<String, List<NonPersistedEventEnvelope>> entry : eventsPerPartitionKey.entrySet()) {
      String partitionKey = entry.getKey();
      int chunkSize = config.azureConfiguration.cosmos.batchSize;
      for (List<NonPersistedEventEnvelope> eventListInChunk : chunkEvents(entry.getValue(), chunkSize)) {
        List<EventEnvelope> chunkWithCreatedAt = toEventEnvelopes(eventListInChunk);
        envelopesWithCreatedAt.addAll(chunkWithCreatedAt);
        List<OperationInput> inputOperations = toInputOperations(chunkWithCreatedAt, config);
        BatchResponse batchResponse = batchEvents(cosmosDb, config, inputOperations, partitionKey);

        // Batch is transactional and will roll back all operations if one fails
        if (batchResponse == null || batchResponse.code() != 200) {
          String json = JsValues.stringify(batchResponse);
          logger.error("An error ocurred storing a batch of events. Batch response: " + json);
          Integer substatus = batchResponse == null ? null : batchResponse.substatus();
          throw new IllegalStateException(
              "Error " + (substatus == null ? "undefined" : substatus) + " storing events: " + json);
        }
        logger.debug("EventEnvelopes with " + partitionKey + " stored: " + JsValues.jsString(batchResponse.result()));
      }
    }
    return envelopesWithCreatedAt;
  }

  private static List<EventEnvelope> toEventEnvelopes(List<NonPersistedEventEnvelope> events) {
    List<EventEnvelope> result = new ArrayList<>();
    for (NonPersistedEventEnvelope event : events) {
      result.add(event.persisted(Dates.nowIso()));
    }
    return result;
  }

  private static List<OperationInput> toInputOperations(List<EventEnvelope> events, BoosterConfig config) {
    List<OperationInput> operations = new ArrayList<>();
    for (EventEnvelope event : events) {
      operations.add(OperationInput.create(storableResource(config, event)));
    }
    return operations;
  }

  private static BatchResponse batchEvents(
      CosmosClient cosmosDb, BoosterConfig config, List<OperationInput> inputOperations, String partitionKey) {
    Logger logger = Loggers.getLogger(config, "store-events-adapter#batchEvents");
    try {
      logger.debug("Storing EventEnvelopes with inputOperations:", inputOperations);
      return cosmosDb.database(config.resourceNames().applicationStack())
          .container(config.resourceNames().eventsStore())
          .items()
          .batch(inputOperations, partitionKey, RequestOptions.from(config.azureConfiguration.cosmos.requestOptions));
    } catch (RuntimeException e) {
      logger.error("Unexpected error storing events", e);
      throw e;
    }
  }

  /**
   * {@code storableResource}: {@code { ...eventEnvelope, entityTypeName_entityID_kind: <pk>, createdAt }}.
   *
   * @param config the configuration
   * @param eventEnvelope the event with its {@code createdAt}
   * @return the document
   */
  static Map<String, Object> storableResource(BoosterConfig config, EventEnvelope eventEnvelope) {
    Logger logger = Loggers.getLogger(config, "store-events-adapter#storableResource");
    try {
      Map<String, Object> document = new LinkedHashMap<>(BoosterJson.toRawMap(eventEnvelope));
      document.put(EventsStoreAttributes.PARTITION_KEY,
          PartitionKeys.partitionKeyForEvent(eventEnvelope.entityTypeName(), eventEnvelope.entityID()));
      document.put(EventsStoreAttributes.SORT_KEY, eventEnvelope.createdAt());
      return document;
    } catch (RuntimeException e) {
      logger.error("Could not parse eventEnvelope " + JsValues.jsString(eventEnvelope) + " to JSONObject", e);
      throw e;
    }
  }

  /**
   * {@code chunkEvents}: {@code Array.from({ length: Math.ceil(n / size) }, (_, i) => arr.slice(i * size, ...))}. A
   * size of 0 throws like JavaScript's {@code RangeError: Invalid array length}; a negative size yields no chunk.
   */
  static <T> List<List<T>> chunkEvents(List<T> events, int size) {
    List<List<T>> chunks = new ArrayList<>();
    if (events.isEmpty() || size < 0) {
      return chunks;
    }
    if (size == 0) {
      throw new IllegalArgumentException("Invalid array length");
    }
    for (int i = 0; i < events.size(); i += size) {
      chunks.add(new ArrayList<>(events.subList(i, Math.min(events.size(), i + size))));
    }
    return chunks;
  }
}
