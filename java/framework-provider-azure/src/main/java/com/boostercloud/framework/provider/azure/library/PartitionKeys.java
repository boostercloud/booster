package com.boostercloud.framework.provider.azure.library;

/**
 * Port of {@code library/partition-keys.ts}: the values of the events container partition key
 * ({@code entityTypeName_entityID_kind}) and of the Event Hub partition key.
 */
public final class PartitionKeys {

  private PartitionKeys() {}

  /**
   * @param entityTypeName the entity class name
   * @param entityID the entity id (a {@code UUID}, a string, ...)
   * @return {@code <entityTypeName>-<entityID>-event}
   */
  public static String partitionKeyForEvent(String entityTypeName, Object entityID) {
    return partitionKey(entityTypeName, entityID, "event");
  }

  /**
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @return {@code <entityTypeName>-<entityID>-snapshot}
   */
  public static String partitionKeyForSnapshot(String entityTypeName, Object entityID) {
    return partitionKey(entityTypeName, entityID, "snapshot");
  }

  /**
   * The template literal {@code `${entityTypeName}-${entityID}-${kind}`} (absent values render as
   * {@code undefined}), also re-implemented privately by {@code events-searcher-builder.ts} and
   * {@code event-delete-adapter.ts}.
   *
   * @param entityTypeName the entity class name
   * @param entityID the entity id
   * @param kind {@code event} or {@code snapshot}
   * @return the partition key value
   */
  static String partitionKey(String entityTypeName, Object entityID, String kind) {
    return (entityTypeName == null ? "undefined" : entityTypeName) + "-"
        + (entityID == null ? "undefined" : entityID.toString()) + "-" + kind;
  }
}
