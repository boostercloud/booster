package com.boostercloud.framework.provider.azure.cosmos;

/**
 * Partition key helpers. {@link #UNDEFINED} stands for a document that lacks the partition key property (header
 * {@code [{}]} in the REST API), which differs from a {@code null} partition key value (header {@code [null]}).
 */
public enum PartitionKeyValue {
  /** The partition key property is absent from the document. */
  UNDEFINED
}
