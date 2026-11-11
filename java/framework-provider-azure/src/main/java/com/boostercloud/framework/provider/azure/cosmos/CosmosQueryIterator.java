package com.boostercloud.framework.provider.azure.cosmos;

/** A query in progress ({@code QueryIterator} of {@code @azure/cosmos}). */
public interface CosmosQueryIterator {
  /**
   * {@code fetchAll()}: every result, following continuation tokens.
   *
   * @return all the results (no continuation token)
   */
  FeedResponse fetchAll();

  /**
   * {@code fetchNext()}: the next page of at most {@code maxItemCount} results.
   *
   * @return the page and the continuation token for the next one ({@code null} at the end)
   */
  FeedResponse fetchNext();

  /**
   * @return whether {@link #fetchNext()} may return more results
   */
  boolean hasMoreResults();
}
