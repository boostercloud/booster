package com.boostercloud.framework.provider.azure.infrastructure.testhelper;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import java.util.Map;
import java.util.Objects;

/**
 * Port of {@code test-helper/azure-counters.ts}: counts the documents of the application's Cosmos DB containers
 * (cross-partition {@code SELECT COUNT(1) as total} queries). The methods match application-tester's {@code Counters}.
 */
public final class AzureCounters {

  private final String appName;
  private final CosmosClient db;

  /**
   * @param appName the application name
   * @param db the Cosmos DB client of the account
   */
  public AzureCounters(String appName, CosmosClient db) {
    this.appName = Objects.requireNonNull(appName, "appName");
    this.db = Objects.requireNonNull(db, "db");
  }

  /**
   * @return the documents of {@code <app>-app-subscriptions-store}
   */
  public long subscriptions() {
    return countTableItems("subscriptions-store");
  }

  /**
   * @return the documents of {@code <app>-app-connections-store}
   */
  public long connections() {
    return countTableItems("connections-store");
  }

  /**
   * @return the events ({@code kind = "event"}) of {@code <app>-app-events-store}
   */
  public long events() {
    return itemsCount(appName + "-app-events-store", "SELECT COUNT(1) as total FROM c WHERE c.kind=\"event\"");
  }

  /**
   * @param readModelName the read model
   * @return the documents of {@code <app>-app-<readModelName>}
   */
  public long readModels(String readModelName) {
    return itemsCount(appName + "-app-" + readModelName, "SELECT COUNT(1) as total FROM c");
  }

  /**
   * @param table the container
   * @param query the count query
   * @return {@code resources[0].total}, or {@code -1} when the query returns nothing (TypeScript {@code undefined})
   */
  private long itemsCount(String table, String query) {
    FeedResponse response = db.database(appName + "-app").container(table).items().query(SqlQuerySpec.of(query))
        .fetchAll();
    if (!response.resources().isEmpty() && response.resources().get(0) instanceof Map<?, ?> first
        && first.get("total") instanceof Number total) {
      return total.longValue();
    }
    return -1;
  }

  private long countTableItems(String tableName) {
    return itemsCount(appName + "-app-" + tableName, "SELECT COUNT(1) as total FROM c");
  }
}
