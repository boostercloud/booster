package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.provider.local.services.WebSocketRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphQL subscriptions storage. Port of {@code library/subscription-adapter.ts}.
 */
public final class SubscriptionAdapter {

  private SubscriptionAdapter() {}

  /**
   * {@code subscribeToReadModel(db, config, subscriptionEnvelope)}: stores the whole envelope (its {@code type} class
   * is not stored; core resolves it from {@code className}).
   *
   * @param db the subscriptions registry
   * @param config the configuration
   * @param subscriptionEnvelope the subscription
   */
  public static void subscribeToReadModel(WebSocketRegistry db, BoosterConfig config,
      SubscriptionEnvelope subscriptionEnvelope) {
    db.store(subscriptionEnvelope);
  }

  /**
   * {@code fetchSubscriptions(db, config, subscriptionName)}: every subscription to a read model, of any connection.
   *
   * @param db the subscriptions registry
   * @param config the configuration
   * @param subscriptionName the read model name
   * @return the subscriptions
   */
  public static List<SubscriptionEnvelope> fetchSubscriptions(WebSocketRegistry db, BoosterConfig config,
      String subscriptionName) {
    List<SubscriptionEnvelope> subscriptions = new ArrayList<>();
    for (Map<String, Object> document : db.query(Map.of("className", subscriptionName))) {
      subscriptions.add(BoosterJson.convert(document, SubscriptionEnvelope.class));
    }
    return subscriptions;
  }

  /**
   * {@code deleteSubscription(db, config, connectionID, subscriptionID)}: deletes one subscription of a connection.
   *
   * <p>Fixed TypeScript bug: TypeScript filters on a top-level {@code subscriptionID} field that subscription documents
   * do not have, so a GraphQL {@code stop} never deletes anything. The subscription id is the id of the subscribed
   * operation, so this port matches {@code operation.id}.
   *
   * @param db the subscriptions registry
   * @param config the configuration
   * @param connectionID the connection id
   * @param subscriptionID the subscription id ({@code operation.id})
   */
  public static void deleteSubscription(WebSocketRegistry db, BoosterConfig config, String connectionID,
      String subscriptionID) {
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("connectionID", connectionID);
    query.put("operation.id", subscriptionID);
    db.delete(query);
  }

  /**
   * {@code deleteAllSubscriptions(db, config, connectionID)}.
   *
   * @param db the subscriptions registry
   * @param config the configuration
   * @param connectionID the connection id
   */
  public static void deleteAllSubscriptions(WebSocketRegistry db, BoosterConfig config, String connectionID) {
    db.delete(Map.of("connectionID", connectionID));
  }
}
