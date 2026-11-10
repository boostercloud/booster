package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.eventhubs.CreateBatchOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventData;
import com.boostercloud.framework.provider.azure.eventhubs.EventDataBatch;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubProducerClient;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.List;

/**
 * Port of {@code library/events-stream-producer-adapter.ts}: publishes the events of one entity to Event Hubs in
 * size-limited batches, all with the partition key {@code <entity>-<id>-event} so they keep their order.
 */
public final class EventsStreamProducerAdapter {

  private EventsStreamProducerAdapter() {}

  /**
   * {@code produceEventsStream}. Each message body is the event document as the change feed delivered it (system
   * properties included; the consumer de-duplicates on {@code _etag}); envelopes this provider did not read are sent
   * as their JSON form.
   *
   * @param producer the producer
   * @param entityName the entity class name
   * @param entityID the entity id
   * @param eventEnvelopes the events of that entity
   * @param config the configuration
   * @throws IllegalStateException {@code Message was too large and can't be sent until it's made smaller. <JSON>}
   *     when one event does not fit an empty batch, {@code Not all messages were sent (<sent>/<total>)} when counts
   *     differ
   */
  public static void produceEventsStream(
      EventHubProducerClient producer,
      String entityName,
      Object entityID,
      List<EventEnvelope> eventEnvelopes,
      BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-stream-producer#produceEventsStream");
    logger.debug("Producing eventEnvelopes", eventEnvelopes);
    CreateBatchOptions batchOptions =
        CreateBatchOptions.withPartitionKey(PartitionKeys.partitionKeyForEvent(entityName, entityID));

    EventDataBatch batch = producer.createBatch(batchOptions);
    int numEventsSent = 0;
    int i = 0;
    while (i < eventEnvelopes.size()) {
      // messages can fail to be added to the batch if they exceed the maximum size configured for the EventHub.
      EventEnvelope eventEnvelope = eventEnvelopes.get(i);
      Object body = bodyFor(eventEnvelope);
      boolean isAdded = batch.tryAdd(new EventData(body));

      if (isAdded) {
        logger.info("Added " + JsValues.stringify(body) + " to the batch");
        ++i;
        continue;
      }

      if (batch.count() == 0) {
        throw new IllegalStateException(
            "Message was too large and can't be sent until it's made smaller. " + JsValues.stringify(body));
      }

      // We reached the batch size limit
      logger.info("Sending " + batch.count() + " messages");
      producer.sendBatch(batch);
      numEventsSent += batch.count();

      batch = producer.createBatch(batchOptions);
    }

    if (batch.count() > 0) {
      logger.info("Sending remaining " + batch.count() + " messages");
      producer.sendBatch(batch);
      numEventsSent += batch.count();
    }

    logger.info("Sent " + numEventsSent + " events");

    if (numEventsSent != eventEnvelopes.size()) {
      throw new IllegalStateException("Not all messages were sent (" + numEventsSent + "/" + eventEnvelopes.size() + ")");
    }
  }

  private static Object bodyFor(EventEnvelope eventEnvelope) {
    Object document = EventDocuments.documentFor(eventEnvelope);
    return document != null ? document : BoosterJson.toRawMap(eventEnvelope);
  }
}
