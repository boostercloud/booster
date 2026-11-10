package com.boostercloud.framework.provider.azure.eventhubs;

/**
 * Options of {@link EventHubProducerClient#createBatch(CreateBatchOptions)} ({@code CreateBatchOptions}).
 *
 * @param partitionKey the partition key hashed by the service to choose the partition, or {@code null}
 * @param maxSizeInBytes the maximum batch size, or {@code null} for the client default
 */
public record CreateBatchOptions(String partitionKey, Long maxSizeInBytes) {

  /**
   * @param partitionKey the partition key
   * @return options with only a partition key ({@code { partitionKey }})
   */
  public static CreateBatchOptions withPartitionKey(String partitionKey) {
    return new CreateBatchOptions(partitionKey, null);
  }
}
