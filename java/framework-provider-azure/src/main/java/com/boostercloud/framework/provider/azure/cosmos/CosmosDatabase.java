package com.boostercloud.framework.provider.azure.cosmos;

/** A database handle ({@code Database} of {@code @azure/cosmos}). */
public interface CosmosDatabase {
  /**
   * @return the database id
   */
  String id();

  /**
   * @return the resource link {@code dbs/<id>} ({@code database.url})
   */
  String url();

  /**
   * @param containerId the container id
   * @return a handle (no request is made)
   */
  CosmosContainer container(String containerId);
}
