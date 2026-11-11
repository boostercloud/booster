package com.boostercloud.framework.provider.azure.eventhubs;

/**
 * The Event Hubs producer the stream adapter uses: the subset of {@code @azure/event-hubs}'s
 * {@code EventHubProducerClient} Booster calls. The production implementation is
 * {@link com.boostercloud.framework.provider.azure.eventhubs.rest.EventHubsRestProducerClient}.
 */
public interface EventHubProducerClient {
  /**
   * {@code producer.createBatch(options)}.
   *
   * @param options the batch options (partition key)
   * @return an empty batch
   */
  EventDataBatch createBatch(CreateBatchOptions options);

  /**
   * {@code producer.sendBatch(batch)}.
   *
   * @param batch a batch created by this client
   * @throws EventHubsException when the service rejects the batch after the configured retries
   */
  void sendBatch(EventDataBatch batch);
}
