package com.boostercloud.framework.provider.azure.infrastructure.testhelper;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import java.util.Objects;

/**
 * Port of {@code test-helper/azure-counters.ts}: counts the documents of the application's Cosmos DB containers with
 * cross-partition count queries. The methods match application-tester's {@code Counters}.
 *
 * <p>TypeScript runs {@code SELECT COUNT(1) as total FROM c} and reads {@code resources[0]?.total}. That is a
 * <em>non-{@code VALUE}</em> aggregate, which a cross-partition query can only run through a client-side
 * {@code GROUP BY} pipeline ({@code NonValueAggregate} query feature) that the Booster Cosmos DB REST client does not
 * implement (it supports {@code SELECT VALUE} aggregates). The counters therefore run the equivalent
 * {@code SELECT VALUE COUNT(1) FROM c} (with the same {@code WHERE} clause) and read {@code resources[0]}: the counts
 * are the same.
 */
public final class AzureCounters {

  /** {@code SELECT COUNT(1) as total FROM c} as a {@code VALUE} aggregate. */
  public static final String COUNT_QUERY = "SELECT VALUE COUNT(1) FROM c";

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
    return itemsCount(appName + "-app-events-store", COUNT_QUERY + " WHERE c.kind=\"event\"");
  }

  /**
   * @param readModelName the read model
   * @return the documents of {@code <app>-app-<readModelName>}
   */
  public long readModels(String readModelName) {
    return itemsCount(appName + "-app-" + readModelName, COUNT_QUERY);
  }

  /**
   * @param table the container
   * @param query the count query
   * @return {@code resources[0]}, or {@code -1} when the query returns nothing (TypeScript {@code undefined})
   */
  private long itemsCount(String table, String query) {
    FeedResponse response = db.database(appName + "-app").container(table).items().query(SqlQuerySpec.of(query))
        .fetchAll();
    if (!response.resources().isEmpty() && response.resources().get(0) instanceof Number total) {
      return total.longValue();
    }
    return -1;
  }

  private long countTableItems(String tableName) {
    return itemsCount(appName + "-app-" + tableName, COUNT_QUERY);
  }
}
