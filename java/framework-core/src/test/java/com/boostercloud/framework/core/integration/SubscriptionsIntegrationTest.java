package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLResponse;
import com.boostercloud.framework.types.Dates;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * GraphQL over websockets with the {@code subscriptions-transport-ws} protocol: the connection handshake with a
 * token, subscriptions by id and filtered, the notifications sent when the read models change, {@code stop},
 * {@code connection_terminate} and disconnection, and queries and mutations sent through the socket.
 */
class SubscriptionsIntegrationTest extends IntegrationTestSupport {

  private static final String CONNECTION = "connection-1";

  private void changeCart(String cartId, int quantity) {
    data(graphQL("mutation { ChangeCartItem(input: {cartId: \"" + cartId + "\", productId: \"p\", quantity: "
        + quantity + "}) }"));
  }

  private void init(String connectionID, String token) {
    socketConnect(connectionID);
    socketSend(connectionID, map("type", "connection_init",
        "payload", token == null ? map() : map("Authorization", "Bearer " + token)));
  }

  private void start(String connectionID, String id, String query) {
    socketSend(connectionID, map("type", "start", "id", id, "payload", map("query", query)));
  }

  @Test
  void connectingAnswersWithTheSubProtocol() {
    GraphQLResponse response = socketConnect(CONNECTION);

    assertThat(response.result()).isNull();
    assertThat(response.headers()).isEqualTo(Map.of("Sec-WebSocket-Protocol", "graphql-ws"));
  }

  @Test
  void theHandshakeStoresTheUserOfTheToken() {
    init(CONNECTION, TestTokens.customer());

    assertThat(socketMessages(CONNECTION)).containsExactly(Map.of("type", "connection_ack"));
    assertThat(provider.connections().data(CONNECTION)).hasValueSatisfying(data -> {
      assertThat(data.user().username()).isEqualTo("customer@store.test");
      assertThat(data.expirationTime()).isBetween(Dates.nowEpochSeconds() + 604790, Dates.nowEpochSeconds() + 604810);
    });
  }

  @Test
  void aConnectionStopsActingAsItsUserOnceItsTokenExpires() {
    init(CONNECTION, TestTokens.customer());
    start(CONNECTION, "sub-1", "subscription { CartReadModel(id: \"cart-1\") { id } }");
    changeCart("cart-1", 1);
    int notifiedWhileValid = socketMessages(CONNECTION).size();

    // The token expires an hour after it was issued
    Dates.useClock(Clock.offset(Clock.systemUTC(), Duration.ofSeconds(3601)));
    changeCart("cart-1", 2);
    start(CONNECTION, "q-1", "query { CartReadModel(id: \"cart-1\") { id } }");

    List<Map<String, Object>> messages = socketMessages(CONNECTION);
    assertThat(notifiedWhileValid).isGreaterThan(1);
    assertThat(messages.subList(notifiedWhileValid, messages.size())).containsExactly(Map.of("type",
        "connection_error", "payload", "The token of the connection has expired: send connection_init with a new token"));
    assertThat(provider.readModels().subscriptions()).isEmpty();
    assertThat(provider.connections().data(CONNECTION)).isEmpty();

    init(CONNECTION, TestTokens.customer());
    start(CONNECTION, "q-2", "query { CartReadModel(id: \"cart-1\") { id } }");
    assertThat(socketMessages(CONNECTION)).extracting(message -> message.get("type") + ":" + message.get("id"))
        .endsWith("connection_ack:null", "data:q-2", "complete:q-2");
  }

  @Test
  void aConnectionLastsAtMostTheConfiguredDuration() {
    config.subscriptions.maxConnectionDurationInSeconds = 60;
    init(CONNECTION, null);

    Dates.useClock(Clock.offset(Clock.systemUTC(), Duration.ofSeconds(61)));
    start(CONNECTION, "q-1", "query { CartReadModel(id: \"cart-1\") { id } }");

    assertThat(socketMessages(CONNECTION)).containsExactly(Map.of("type", "connection_ack"), Map.of("type",
        "connection_error", "payload", "The connection has expired: send connection_init to authenticate it again"));
    assertThat(provider.connections().data(CONNECTION)).isEmpty();
  }

  @Test
  void anInvalidTokenFailsTheHandshake() {
    init(CONNECTION, TestTokens.forgedAdmin());

    assertThat(socketMessages(CONNECTION)).containsExactly(
        Map.of("type", "connection_error", "payload", "JsonWebTokenError: invalid signature"));
    assertThat(provider.connections().data(CONNECTION)).isEmpty();
  }

  @Test
  void aSubscriptionByIdIsNotifiedOfEveryChangeOfItsReadModel() {
    init(CONNECTION, null);
    start(CONNECTION, "sub-1", "subscription { CartReadModel(id: \"cart-1\") { id checks totalQuantity } }");

    assertThat(provider.readModels().subscriptions()).singleElement().satisfies(subscription -> {
      assertThat(subscription).containsEntry("className", "CartReadModel").containsEntry("connectionID", CONNECTION)
          .containsEntry("filters", Map.of("id", Map.of("eq", "cart-1")));
      assertThat(at(subscription, "operation", "id")).isEqualTo("sub-1");
    });
    changeCart("cart-2", 5);
    changeCart("cart-1", 2);

    // Two projections (after CartItemChanged, then after the CartChecked its handler registered): the first one may
    // already include the check, since the handler and the projection of an event run concurrently
    List<Map<String, Object>> messages = socketMessages(CONNECTION);
    assertThat(messages).hasSize(3);
    assertThat(messages.getFirst()).isEqualTo(Map.of("type", "connection_ack"));
    assertThat(messages.get(1)).containsEntry("type", "data").containsEntry("id", "sub-1");
    assertThat(mapAt(messages.get(1), "payload", "data", "CartReadModel"))
        .containsEntry("id", "cart-1").containsEntry("totalQuantity", 2);
    assertThat(messages.get(2)).isEqualTo(Map.of("type", "data", "id", "sub-1", "payload",
        Map.of("data", Map.of("CartReadModel", map("id", "cart-1", "checks", 1, "totalQuantity", 2)))));
  }

  @Test
  void aFilteredSubscriptionOnlyReceivesTheMatchingReadModels() {
    init(CONNECTION, null);
    start(CONNECTION, "big", "subscription { CartReadModels(filter: {checks: {gte: 1}}) { id checks } }");

    changeCart("cart-1", 1);
    changeCart("cart-2", 1);

    // Each cart is projected twice; the first projection only matches when it already includes the check
    assertThat(socketMessages(CONNECTION)).filteredOn(message -> "data".equals(message.get("type")))
        .extracting(message -> at(message, "payload", "data", "CartReadModels"))
        .containsOnly(map("id", "cart-1", "checks", 1), map("id", "cart-2", "checks", 1))
        .contains(map("id", "cart-1", "checks", 1), map("id", "cart-2", "checks", 1));
  }

  @Test
  void stoppingASubscriptionCompletesItAndStopsTheNotifications() {
    init(CONNECTION, null);
    start(CONNECTION, "sub-1", "subscription { CartReadModel(id: \"cart-1\") { id } }");
    start(CONNECTION, "sub-2", "subscription { CartReadModel(id: \"cart-1\") { checks } }");

    socketSend(CONNECTION, map("type", "stop", "id", "sub-1"));
    changeCart("cart-1", 1);

    assertThat(provider.readModels().subscriptions()).extracting(subscription -> at(subscription, "operation", "id"))
        .containsExactly("sub-2");
    assertThat(socketMessages(CONNECTION)).extracting(message -> message.get("type") + ":" + message.get("id"))
        .containsExactly("connection_ack:null", "complete:sub-1", "data:sub-2", "data:sub-2");
  }

  @Test
  void terminatingOrDisconnectingDeletesTheConnectionAndItsSubscriptions() {
    init(CONNECTION, TestTokens.customer());
    init("connection-2", null);
    start(CONNECTION, "a", "subscription { CartReadModel(id: \"cart-1\") { id } }");
    start("connection-2", "b", "subscription { CartReadModel(id: \"cart-1\") { id } }");
    start("connection-2", "c", "subscription { CartReadModels { id } }");

    socketSend(CONNECTION, map("type", "connection_terminate"));
    assertThat(provider.connections().data(CONNECTION)).isEmpty();
    assertThat(provider.readModels().subscriptions()).extracting(subscription -> subscription.get("connectionID"))
        .containsOnly("connection-2");

    GraphQLResponse response = socketDisconnect("connection-2");
    assertThat(response.result()).isNull();
    assertThat(response.headers()).isNull();
    assertThat(provider.readModels().subscriptions()).isEmpty();
    assertThat(provider.connections().data("connection-2")).isEmpty();
  }

  @Test
  void queriesAndMutationsThroughTheSocketAnswerDataAndComplete() {
    init(CONNECTION, TestTokens.admin());

    socketSend(CONNECTION, map("type", "start", "id", "m", "payload", map(
        "query", "mutation ($id: ID!) { ChangeCartItem(input: {cartId: $id, productId: \"p\", quantity: 4}) }",
        "variables", map("id", "cart-1"))));
    socketSend(CONNECTION, map("type", "start", "id", "q",
        "payload", map("query", "{ CartTotalQuantity(input: {cartId: \"cart-1\"}) }")));

    assertThat(socketMessages(CONNECTION)).containsExactly(
        Map.of("type", "connection_ack"),
        Map.of("type", "data", "id", "m", "payload", Map.of("data", Map.of("ChangeCartItem", true))),
        Map.of("type", "complete", "id", "m"),
        Map.of("type", "data", "id", "q", "payload", Map.of("data", Map.of("CartTotalQuantity", 40))),
        Map.of("type", "complete", "id", "q"));
  }

  @Test
  void theSocketMessageResponseIsMarked() {
    GraphQLResponse response = socketSend(CONNECTION, map("type", "connection_init", "payload", map()));

    assertThat(response.result()).isNull();
    assertThat(response.headers()).containsEntry("X-Booster-WebSocket-Message", "true");
  }

  @Test
  void aSubscriptionToAProtectedReadModelUsesTheUserOfTheConnection() {
    init(CONNECTION, null);
    init("customer-connection", TestTokens.customer());

    start(CONNECTION, "anonymous", "subscription { ProductReadModels { sku } }");
    start("customer-connection", "customer", "subscription { ProductReadModels { sku } }");
    graphQL("mutation { CreateProduct(input: {sku: \"S\", displayName: \"S\", price: 1, category: \"c\"}) }",
        TestTokens.admin());

    // graphql-js serialises the errors of a failed subscription set-up as they are: no Booster extensions
    assertThat(socketMessages(CONNECTION)).containsExactly(Map.of("type", "connection_ack"),
        Map.of("type", "data", "id", "anonymous", "payload", Map.of("errors", List.of(Map.of(
            "message", "Access denied for this resource",
            "locations", List.of(Map.of("line", 1, "column", 16)), "path", List.of("ProductReadModels"))))),
        Map.of("type", "complete", "id", "anonymous"));
    assertThat(provider.readModels().subscriptions()).extracting(subscription -> subscription.get("connectionID"))
        .containsExactly("customer-connection");
    assertThat(socketMessages("customer-connection")).contains(Map.of("type", "data", "id", "customer", "payload",
        Map.of("data", Map.of("ProductReadModels", Map.of("sku", "S")))));
  }

  @Test
  void aStartWithoutQueryIsAnErrorAndUnknownMessagesFailTheConnection() {
    init(CONNECTION, null);

    socketSend(CONNECTION, map("type", "start", "id", "x", "payload", map()));
    socketSend(CONNECTION, map("type", "stop"));

    assertThat(socketMessages(CONNECTION)).containsExactly(
        Map.of("type", "connection_ack"),
        Map.of("type", "error", "id", "x", "payload",
            Map.of("errors", List.of(Map.of("message",
                "Message payload is invalid it must contain at least the \"query\" property")))),
        Map.of("type", "connection_error", "payload", "Missing \"id\" in stop message"));
  }

  @Test
  void subscriptionsOverHttpAreRejected() {
    Map<String, Object> result = graphQL("subscription { CartReadModel(id: \"c\") { id } }");

    assertThat(errorMessage(result)).isEqualTo("This API and protocol does not support \"subscription\" operations,"
        + " only \"query\" and \"mutation\". Use the socket API for \"subscription\"");
  }
}
