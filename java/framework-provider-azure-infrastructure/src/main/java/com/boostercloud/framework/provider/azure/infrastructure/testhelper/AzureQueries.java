package com.boostercloud.framework.provider.azure.infrastructure.testhelper;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Port of {@code test-helper/azure-queries.ts}: raw reads of the events store and the read model containers. The
 * methods match application-tester's {@code Queries}.
 */
public final class AzureQueries {

  private final String appName;
  private final CosmosClient db;

  /**
   * @param appName the application name
   * @param db the Cosmos DB client of the account
   */
  public AzureQueries(String appName, CosmosClient db) {
    this.appName = Objects.requireNonNull(appName, "appName");
    this.db = Objects.requireNonNull(db, "db");
  }

  /**
   * @param primaryKey the partition key ({@code <entity>-<id>-event})
   * @return the events with that key, newest first
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> events(String primaryKey) {
    List<Object> resources = db.database(appName + "-app").container(appName + "-app-events-store").items()
        .query(SqlQuerySpec.of(
            "SELECT * from c WHERE c.entityTypeName_entityID_kind = @eventId order by c.createdAt DESC",
            SqlParameter.of("@eventId", primaryKey)))
        .fetchAll()
        .resources();
    List<Map<String, Object>> events = new ArrayList<>();
    for (Object resource : resources) {
      events.add((Map<String, Object>) resource);
    }
    return events;
  }

  /**
   * @param primaryKey the read model id (also its partition key)
   * @param readModelName the read model
   * @return a one-element list with the document, {@code [null]} when it does not exist (TypeScript
   *     {@code [undefined]})
   */
  public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
    ItemResponse response = db.database(appName + "-app").container(appName + "-app-" + readModelName)
        .item(primaryKey, primaryKey).read();
    List<Map<String, Object>> result = new ArrayList<>();
    result.add(response.resource());
    return result;
  }
}
