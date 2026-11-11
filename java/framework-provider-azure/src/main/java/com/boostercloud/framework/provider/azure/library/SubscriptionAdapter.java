package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;
import static com.boostercloud.framework.provider.azure.helpers.JsValues.truthy;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.provider.azure.Constants.SubscriptionsStoreAttributes;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Port of {@code library/subscription-adapter.ts}: GraphQL subscriptions stored in the subscriptions container
 * (partition key {@code /className}).
 */
public final class SubscriptionAdapter {

  /** The indentation of the TypeScript template literals, kept in the SQL text. */
  private static final String INDENT = "\n              ";

  static final String FETCH_SUBSCRIPTIONS_QUERY = "SELECT *" + INDENT + "FROM c" + INDENT + "WHERE c[\""
      + SubscriptionsStoreAttributes.PARTITION_KEY + "\"] = @partitionKey";
  static final String DELETE_SUBSCRIPTION_QUERY = "SELECT *" + INDENT + "FROM c" + INDENT + "WHERE c[\""
      + SubscriptionsStoreAttributes.INDEX_BY_CONNECTION_ID_PARTITION_KEY + "\"] = @partitionKey"
      + "\n                AND c[\"" + SubscriptionsStoreAttributes.INDEX_BY_CONNECTION_ID_SORT_KEY + "\"] = @sortKey";
  static final String DELETE_ALL_SUBSCRIPTIONS_QUERY = "SELECT *" + INDENT + "FROM c" + INDENT + "WHERE c[\""
      + SubscriptionsStoreAttributes.INDEX_BY_CONNECTION_ID_PARTITION_KEY + "\"] = @partitionKey";

  private SubscriptionAdapter() {}

  /**
   * {@code subscribeToReadModel}: stores {@code { ...envelope, connectionID_subscriptionID, subscriptionID, ttl }}
   * ({@code ttl = expirationTime}, an absolute epoch, see quirk Q2).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param subscriptionEnvelope the subscription
   * @throws IllegalStateException when {@code className}, {@code connectionID}, {@code operation.id} or
   *     {@code expirationTime} is missing (message kept verbatim, unbalanced quote included)
   */
  public static void subscribeToReadModel(
      CosmosClient cosmosDb, BoosterConfig config, SubscriptionEnvelope subscriptionEnvelope) {
    Object operationId = subscriptionEnvelope.operation() == null ? null : subscriptionEnvelope.operation().id();
    if (!truthy(subscriptionEnvelope.className())
        || !truthy(subscriptionEnvelope.connectionID()) // First part of the sort key
        || !truthy(operationId) // Second part of the sort key
        || !truthy(subscriptionEnvelope.expirationTime())) {
      throw new IllegalStateException("Subscription envelope is missing any of the following required attributes: "
          + "\"" + SubscriptionsStoreAttributes.PARTITION_KEY + "\", \"connectionID\", \"operation.id\", "
          + SubscriptionsStoreAttributes.TTL + "\"");
    }

    long ttl = subscriptionEnvelope.expirationTime();
    Map<String, Object> document = new LinkedHashMap<>(BoosterJson.toRawMap(subscriptionEnvelope));
    document.put(SubscriptionsStoreAttributes.SORT_KEY, subscriptionEnvelope.connectionID() + "-" + operationId);
    document.put(SubscriptionsStoreAttributes.INDEX_BY_CONNECTION_ID_SORT_KEY, operationId);
    document.put("ttl", ttl);
    subscriptionsContainer(cosmosDb, config).items().create(document);
  }

  /**
   * {@code fetchSubscriptions}: every subscription to a read model (expired ones included, as in TypeScript).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param subscriptionName the read model name
   * @return the subscriptions
   */
  public static List<SubscriptionEnvelope> fetchSubscriptions(
      CosmosClient cosmosDb, BoosterConfig config, String subscriptionName) {
    List<Object> resources = subscriptionsContainer(cosmosDb, config).items()
        .query(new SqlQuerySpec(FETCH_SUBSCRIPTIONS_QUERY, List.of(SqlParameter.of("@partitionKey", subscriptionName))),
            FeedOptions.forPartition(subscriptionName))
        .fetchAll()
        .resources();
    List<SubscriptionEnvelope> subscriptions = new ArrayList<>();
    for (Object resource : resources) {
      subscriptions.add(BoosterJson.convert(resource, SubscriptionEnvelope.class));
    }
    return subscriptions;
  }

  /**
   * {@code deleteSubscription}: deletes the first subscription of the connection with that id (cross-partition
   * query).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param connectionID the connection
   * @param subscriptionID the subscription ({@code operation.id})
   */
  public static void deleteSubscription(
      CosmosClient cosmosDb, BoosterConfig config, String connectionID, String subscriptionID) {
    Logger logger = Loggers.getLogger(config, "subscription-adapter#deleteSubscription");
    List<Object> foundSubscriptions = subscriptionsContainer(cosmosDb, config).items()
        .query(new SqlQuerySpec(DELETE_SUBSCRIPTION_QUERY, List.of(
            SqlParameter.of("@partitionKey", connectionID), SqlParameter.of("@sortKey", subscriptionID))))
        .fetchAll()
        .resources();
    if (foundSubscriptions.isEmpty()) {
      logger.info("[deleteSubscription] No subscriptions found with connectionID=" + connectionID
          + " and subscriptionID=" + subscriptionID);
      return;
    }

    // There can't be more than one, as we used the full primary key in the query
    Object subscriptionToDelete = foundSubscriptions.getFirst();
    logger.debug("[deleteSubscription] Deleting subscription = ", subscriptionToDelete);
    subscriptionsContainer(cosmosDb, config)
        .item(String.valueOf(get(subscriptionToDelete, "id")),
            get(subscriptionToDelete, SubscriptionsStoreAttributes.PARTITION_KEY))
        .delete();
  }

  /**
   * {@code deleteAllSubscriptions}: deletes every subscription of a connection, in parallel, ignoring individual
   * failures ({@code Promise.allSettled}).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param connectionID the connection
   */
  public static void deleteAllSubscriptions(CosmosClient cosmosDb, BoosterConfig config, String connectionID) {
    Logger logger = Loggers.getLogger(config, "subscription-adapter#deleteAllSubscriptions");
    List<Object> foundSubscriptions = subscriptionsContainer(cosmosDb, config).items()
        .query(new SqlQuerySpec(DELETE_ALL_SUBSCRIPTIONS_QUERY, List.of(SqlParameter.of("@partitionKey", connectionID))))
        .fetchAll()
        .resources();
    if (foundSubscriptions.isEmpty()) {
      logger.info("No subscriptions found with connectionID=" + connectionID);
      return;
    }

    logger.debug("Deleting all subscriptions for connectionID=" + connectionID + ", which are: ", foundSubscriptions);
    List<Callable<Object>> deletes = new ArrayList<>();
    for (Object subscriptionRecord : foundSubscriptions) {
      deletes.add(() -> subscriptionsContainer(cosmosDb, config)
          .item(String.valueOf(get(subscriptionRecord, "id")),
              get(subscriptionRecord, SubscriptionsStoreAttributes.PARTITION_KEY))
          .delete());
    }
    Promises.runAllSettled(deletes);
  }

  private static CosmosContainer subscriptionsContainer(CosmosClient cosmosDb, BoosterConfig config) {
    return cosmosDb.database(config.resourceNames().applicationStack())
        .container(config.resourceNames().subscriptionsStore());
  }
}
