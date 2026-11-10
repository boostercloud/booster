package com.boostercloud.framework.provider.azure.cosmos;

/**
 * The Cosmos DB entry point the adapters use: the subset of {@code @azure/cosmos}'s {@code CosmosClient} chain
 * ({@code cosmosDb.database(id).container(id).items...}) that Booster calls. The production implementation is
 * {@link com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient} (REST API over
 * {@code java.net.http}); tests use Mockito mocks.
 */
public interface CosmosClient {
  /**
   * @param databaseId the database id ({@code config.resourceNames().applicationStack()})
   * @return a handle (no request is made)
   */
  CosmosDatabase database(String databaseId);
}
