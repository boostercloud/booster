package com.boostercloud.framework.provider.azure.cosmos;

/**
 * Query options ({@code FeedOptions} of {@code @azure/cosmos}).
 *
 * @param continuationToken the token returned by the previous page, or {@code null}
 * @param maxItemCount the page size, or {@code null} for the service default
 * @param partitionKey the partition key value when the query targets a single logical partition (use
 *     {@link PartitionKeyValue#UNDEFINED} for documents without the partition key property), or {@code null} for a
 *     cross-partition query
 */
public record FeedOptions(String continuationToken, Integer maxItemCount, Object partitionKey) {

  /**
   * @return no options: a cross-partition query with service-default pages
   */
  public static FeedOptions none() {
    return new FeedOptions(null, null, null);
  }

  /**
   * @param partitionKey the partition key value
   * @return options for a single-partition query
   */
  public static FeedOptions forPartition(Object partitionKey) {
    return new FeedOptions(null, null, partitionKey);
  }
}
