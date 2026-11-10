package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClient;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.graphql.GraphQLInitAck;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests of {@code subscription-adapter.ts} and {@code connections-adapter.ts} (no TypeScript tests exist). */
class SubscriptionAndConnectionsAdapterTest {

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private Logger logger;

  @BeforeEach
  void setUp() {
    cosmos = new CosmosMocks();
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
  }

  @AfterEach
  void clearEnvironment() {
    EnvironmentVariables.clearOverrides();
  }

  private SubscriptionEnvelope subscription(String connectionID, String operationId, long expirationTime) {
    return SubscriptionEnvelope.builder()
        .requestID(UUID.of("r1"))
        .className("CartReadModel")
        .version(1)
        .filters(obj("id", obj("eq", "42")))
        .expirationTime(expirationTime)
        .connectionID(connectionID)
        .operation(GraphQLOperation.builder().id(operationId).query("subscription { CartReadModels { id } }").build())
        .build();
  }

  @Nested
  class Subscriptions {

    @Test
    void storesTheSubscriptionWithItsIndexAttributesAndTtl() {
      SubscriptionAdapter.subscribeToReadModel(cosmos.client, config, subscription("conn-1", "1", 1762938902L));

      verify(cosmos.database).container(config.resourceNames().subscriptionsStore());
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> document = ArgumentCaptor.forClass(Map.class);
      verify(cosmos.items).create(document.capture());
      assertThat(document.getValue()).containsExactly(
          Map.entry("requestID", "r1"), Map.entry("className", "CartReadModel"), Map.entry("version", 1),
          Map.entry("filters", obj("id", obj("eq", "42"))), Map.entry("expirationTime", 1762938902),
          Map.entry("connectionID", "conn-1"),
          Map.entry("operation", obj("query", "subscription { CartReadModels { id } }", "id", "1")),
          Map.entry("connectionID_subscriptionID", "conn-1-1"), Map.entry("subscriptionID", "1"),
          Map.entry("ttl", 1762938902L));
    }

    @Test
    void rejectsIncompleteSubscriptionsWithTheTypeScriptMessage() {
      assertThatThrownBy(() -> SubscriptionAdapter.subscribeToReadModel(cosmos.client, config,
          subscription(null, "1", 10)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Subscription envelope is missing any of the following required attributes: \"className\", "
              + "\"connectionID\", \"operation.id\", expirationTime\"");
      assertThatThrownBy(() -> SubscriptionAdapter.subscribeToReadModel(cosmos.client, config,
          subscription("c", "1", 0))).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> SubscriptionAdapter.subscribeToReadModel(cosmos.client, config,
          subscription("c", "", 10))).isInstanceOf(IllegalStateException.class);
      verify(cosmos.items, never()).create(any());
    }

    @Test
    void fetchesTheSubscriptionsOfAReadModelFromItsPartition() {
      cosmos.returnRows(List.of(obj("className", "CartReadModel", "connectionID", "c", "expirationTime", 5, "version", 1,
          "operation", obj("id", "1"), "connectionID_subscriptionID", "c-1", "id", "doc", "_etag", "e")));

      List<SubscriptionEnvelope> subscriptions = SubscriptionAdapter.fetchSubscriptions(cosmos.client, config, "CartReadModel");

      verify(cosmos.items).query(new SqlQuerySpec(
          "SELECT *\n              FROM c\n              WHERE c[\"className\"] = @partitionKey",
          List.of(SqlParameter.of("@partitionKey", "CartReadModel"))), FeedOptions.forPartition("CartReadModel"));
      assertThat(subscriptions).singleElement().satisfies(subscription -> {
        assertThat(subscription.connectionID()).isEqualTo("c");
        assertThat(subscription.operation().id()).isEqualTo("1");
        assertThat(subscription.expirationTime()).isEqualTo(5);
      });
    }

    @Test
    void deleteSubscriptionDeletesOnlyTheFirstMatch() {
      cosmos.returnRows(List.of(obj("id", "d1", "className", "A"), obj("id", "d2", "className", "B")));

      SubscriptionAdapter.deleteSubscription(cosmos.client, config, "conn-1", "sub-1");

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT *\n              FROM c\n              WHERE c[\"connectionID\"] = @partitionKey\n"
              + "                AND c[\"subscriptionID\"] = @sortKey",
          List.of(SqlParameter.of("@partitionKey", "conn-1"), SqlParameter.of("@sortKey", "sub-1"))));
      verify(cosmos.container).item("d1", "A");
      verify(cosmos.container, never()).item(eq("d2"), any());
      verify(cosmos.item).delete();
    }

    @Test
    void deleteSubscriptionWithoutMatchesOnlyLogs() {
      SubscriptionAdapter.deleteSubscription(cosmos.client, config, "conn-1", "sub-1");

      verify(cosmos.item, never()).delete();
      verify(logger).info("[Booster]|subscription-adapter#deleteSubscription: ",
          "[deleteSubscription] No subscriptions found with connectionID=conn-1 and subscriptionID=sub-1");
    }

    @Test
    void deleteAllSubscriptionsDeletesEveryMatchIgnoringFailures() {
      cosmos.returnRows(List.of(obj("id", "d1", "className", "A"), obj("id", "d2", "className", "B")));
      when(cosmos.item.delete()).thenThrow(CosmosException.of("gone", 404)).thenReturn(ItemResponse.of(204, null));

      SubscriptionAdapter.deleteAllSubscriptions(cosmos.client, config, "conn-1");

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT *\n              FROM c\n              WHERE c[\"connectionID\"] = @partitionKey",
          List.of(SqlParameter.of("@partitionKey", "conn-1"))));
      verify(cosmos.container).item("d1", "A");
      verify(cosmos.container).item("d2", "B");
      verify(cosmos.item, times(2)).delete();
    }

    @Test
    void deleteAllSubscriptionsWithoutMatchesOnlyLogs() {
      SubscriptionAdapter.deleteAllSubscriptions(cosmos.client, config, "conn-1");

      verify(logger).info("[Booster]|subscription-adapter#deleteAllSubscriptions: ",
          "No subscriptions found with connectionID=conn-1");
    }
  }

  @Nested
  class Connections {

    @Test
    void storesTheConnectionDataWithItsPartitionKeyAndTtl() {
      ConnectionsAdapter.storeConnectionData(cosmos.client, config, "conn-1",
          new ConnectionDataEnvelope(1763371800L, UserEnvelope.builder().username("jane").build()));

      verify(cosmos.database).container(config.resourceNames().connectionsStore());
      verify(cosmos.items).create(obj("expirationTime", 1763371800,
          "user", obj("username", "jane", "roles", List.of(), "claims", obj()),
          "connectionID", "conn-1", "ttl", 1763371800L));
    }

    @Test
    void fetchesTheFirstStoredData() {
      cosmos.returnRows(List.of(obj("expirationTime", 9, "user", obj("username", "jane"), "connectionID", "conn-1",
          "ttl", 9, "id", "x")));

      Optional<ConnectionDataEnvelope> data = ConnectionsAdapter.fetchConnectionData(cosmos.client, config, "conn-1");

      verify(cosmos.items).query(new SqlQuerySpec(
          "SELECT *\n              FROM c\n              WHERE c[\"connectionID\"] = @partitionKey",
          List.of(SqlParameter.of("@partitionKey", "conn-1"))), FeedOptions.forPartition("conn-1"));
      assertThat(data).contains(new ConnectionDataEnvelope(9, UserEnvelope.builder().username("jane").build()));
      assertThat(ConnectionsAdapter.fetchConnectionData(new CosmosMocks().client, config, "x")).isEmpty();
    }

    @Test
    void deletesTheFirstStoredData() {
      cosmos.returnRows(List.of(obj("id", "x", "connectionID", "conn-1"), obj("id", "y", "connectionID", "conn-1")));

      ConnectionsAdapter.deleteConnectionData(cosmos.client, config, "conn-1");

      verify(cosmos.container).item("x", "conn-1");
      verify(cosmos.item).delete();
    }

    @Test
    void deletingUnknownConnectionsOnlyLogs() {
      ConnectionsAdapter.deleteConnectionData(cosmos.client, config, "conn-1");

      verify(cosmos.item, never()).delete();
      verify(logger).info("[Booster]|connections-adapter#deleteConnectionData: ",
          "No connections found with connectionID=conn-1");
    }

    @Test
    void sendsJsonTextThroughANewWebPubSubClientOnTheBoosterHub() {
      EnvironmentVariables.set("WebPubSubConnectionString", "Endpoint=https://wps;AccessKey=k;Version=1.0;");
      WebPubSubServiceClient serviceClient = mock(WebPubSubServiceClient.class);
      List<String> created = new java.util.ArrayList<>();

      ConnectionsAdapter.sendMessageToConnection((connectionString, hub) -> {
        created.add(connectionString + "|" + hub);
        return serviceClient;
      }, config, "conn-1", new GraphQLInitAck());

      assertThat(created).containsExactly("Endpoint=https://wps;AccessKey=k;Version=1.0;|booster");
      verify(serviceClient).sendToConnection("conn-1", "{\"type\":\"connection_ack\"}", "text/plain");
    }
  }
}
