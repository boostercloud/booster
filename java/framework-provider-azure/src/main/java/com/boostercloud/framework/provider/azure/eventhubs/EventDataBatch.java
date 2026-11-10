package com.boostercloud.framework.provider.azure.eventhubs;

import java.util.List;

/** A size-limited batch of events ({@code EventDataBatch} of {@code @azure/event-hubs}). */
public interface EventDataBatch {
  /**
   * {@code batch.tryAdd(eventData)}.
   *
   * @param eventData the event
   * @return {@code false} when adding it would exceed {@link #maxSizeInBytes()} (the event is not added)
   */
  boolean tryAdd(EventData eventData);

  /**
   * @return the number of events ({@code batch.count})
   */
  int count();

  /**
   * @return the current size in bytes
   */
  long sizeInBytes();

  /**
   * @return the maximum size in bytes
   */
  long maxSizeInBytes();

  /**
   * @return the partition key of every event of the batch, or {@code null}
   */
  String partitionKey();

  /**
   * @return the events added so far
   */
  List<EventData> events();
}
