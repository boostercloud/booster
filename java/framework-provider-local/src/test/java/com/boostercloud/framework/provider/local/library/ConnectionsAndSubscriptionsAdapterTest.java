package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.services.ConnectionData;
import com.boostercloud.framework.provider.local.services.WebSocketRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.graphql.GraphQLInitAck;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests of {@code connections-adapter.ts} and {@code subscription-adapter.ts} (not covered by the TypeScript tests). */
class ConnectionsAndSubscriptionsAdapterTest {

  private BoosterConfig config;
  private Logger logger;
  private WebSocketRegistry registry;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    registry = mock(WebSocketRegistry.class);
  }

  @Test
  void storesConnectionDataWithItsConnectionId() {
    ConnectionDataEnvelope data = new ConnectionDataEnvelope(10, null);
    ConnectionsAdapter.storeConnectionData(registry, config, "c1", data);
    verify(registry).store(new ConnectionData(10, null, "c1"));
    verify(logger).debug("[Booster]|connections-adapter#storeConnectionData: ",
        "Storing the following connection envelope:", data);
  }

  @Test
  void fetchesConnectionDataByConnectionId() {
    when(registry.query(any())).thenReturn(List.of(map("expirationTime", 10, "connectionID", "c1", "_id", "x")));
    assertThat(ConnectionsAdapter.fetchConnectionData(registry, config, "c1"))
        .contains(new ConnectionDataEnvelope(10, null));
    verify(registry).query(map("connectionID", "c1"));
    when(registry.query(any())).thenReturn(List.of());
    assertThat(ConnectionsAdapter.fetchConnectionData(registry, config, "c1")).isEmpty();
  }

  @Test
  void deletesConnectionData() {
    when(registry.delete(any())).thenReturn(1, 0);
    ConnectionsAdapter.deleteConnectionData(registry, config, "c1");
    verify(logger).debug("[Booster]|connections-adapter#deleteConnectionData: ", "Deleted connection = ", "c1");
    ConnectionsAdapter.deleteConnectionData(registry, config, "c1");
    verify(logger).info("[Booster]|connections-adapter#deleteConnectionData: ", "No connections found with connectionID=c1");
    verify(registry, org.mockito.Mockito.times(2)).delete(map("connectionID", "c1"));
  }

  @Test
  void sendsMessagesThroughTheWebSocketServer() {
    WebSocketServerAdapter server = mock(WebSocketServerAdapter.class);
    ConnectionsAdapter.sendMessageToConnection(server, config, "c1", new GraphQLInitAck());
    verify(server).sendToConnection("c1", new GraphQLInitAck());
    verify(logger).debug("[Booster]|connection-adapter#sendMessageToConnection: ",
        "Sending message {\"type\":\"connection_ack\"} to connection c1");
  }

  @Test
  void subscriptions() {
    ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().requestID(UUID.of("r")).className("RM")
        .version(1).filters(map()).build();
    SubscriptionEnvelope subscription = SubscriptionEnvelope.of(request, 5, "c1",
        GraphQLOperation.builder().query("q").id("op").build());
    SubscriptionAdapter.subscribeToReadModel(registry, config, subscription);
    verify(registry).store(subscription);

    when(registry.query(any())).thenReturn(List.of(map("requestID", "r", "className", "RM", "version", 1,
        "filters", map(), "expirationTime", 5, "connectionID", "c1", "operation", map("query", "q", "id", "op"),
        "_id", "x")));
    assertThat(SubscriptionAdapter.fetchSubscriptions(registry, config, "RM")).containsExactly(subscription);
    verify(registry).query(map("className", "RM"));

    SubscriptionAdapter.deleteSubscription(registry, config, "c1", "op");
    verify(registry).delete(map("connectionID", "c1", "operation.id", "op"));
    SubscriptionAdapter.deleteAllSubscriptions(registry, config, "c1");
    verify(registry).delete(map("connectionID", "c1"));
  }
}
