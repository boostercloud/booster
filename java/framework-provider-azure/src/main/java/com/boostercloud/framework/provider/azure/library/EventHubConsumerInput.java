package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.provider.azure.types.AzureFunctionRawRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import java.util.List;

/**
 * Port of {@code EventHubConsumerInput}: an Event Hub input with the binding metadata arrays. As in TypeScript, the
 * Booster consumer function does not provide {@code bindingData} (quirk Q6); the type exists for callers that do.
 *
 * @param messages the messages
 * @param context the invocation context
 * @param bindingData the metadata arrays
 */
public record EventHubConsumerInput(List<Object> messages, AzureInvocationContext context, BindingData bindingData)
    implements AzureFunctionRawRequest {

  /**
   * Event Hub trigger metadata arrays, one entry per message.
   *
   * @param partitionKeyArray partition keys
   * @param offsetArray offsets
   * @param sequenceNumberArray sequence numbers
   * @param enqueuedTimeUtcArray enqueued times
   */
  public record BindingData(
      List<String> partitionKeyArray,
      List<String> offsetArray,
      List<Long> sequenceNumberArray,
      List<String> enqueuedTimeUtcArray) {}
}
