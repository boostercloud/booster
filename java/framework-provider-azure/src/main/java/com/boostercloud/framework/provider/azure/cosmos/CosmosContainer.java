package com.boostercloud.framework.provider.azure.cosmos;

/** A container handle ({@code Container} of {@code @azure/cosmos}). */
public interface CosmosContainer {
  /**
   * @return the container id
   */
  String id();

  /**
   * @return the resource link {@code dbs/<db>/colls/<id>} ({@code container.url})
   */
  String url();

  /**
   * @return the item operations of the container ({@code container.items})
   */
  CosmosItems items();

  /**
   * {@code container.item(id, partitionKeyValue)}.
   *
   * @param id the document id
   * @param partitionKeyValue the partition key value of the document
   * @return a handle (no request is made)
   */
  CosmosItem item(String id, Object partitionKeyValue);
}
