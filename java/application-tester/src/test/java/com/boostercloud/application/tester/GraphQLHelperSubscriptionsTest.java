package com.boostercloud.application.tester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import com.boostercloud.application.tester.fake.FakeGraphQLServer;
import com.boostercloud.application.tester.fake.FakeGraphQLServer.FakeResponse;
import com.boostercloud.application.tester.fake.FakeGraphQLServer.FakeSocket;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import com.boostercloud.application.tester.graphql.GraphQLError;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.application.tester.graphql.ObservableSubscription;
import com.boostercloud.application.tester.graphql.SubscriptionClient;
import com.boostercloud.application.tester.graphql.SubscriptionConnectionException;
import com.boostercloud.application.tester.graphql.SubscriptionObservable;
import com.boostercloud.application.tester.graphql.SubscriptionObserver;
import com.boostercloud.framework.types.Logger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraphQLHelperSubscriptionsTest {

  private static final Duration WAIT = Duration.ofSeconds(15);

  private static final String CART_SUBSCRIPTION = """
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
        }
      }
      """;

  private FakeGraphQLServer server;
  private GraphQLHelper graphql;
  private final List<DisconnectableGraphQLClient> clients = new ArrayList<>();
  private final List<String> socketLog = new CopyOnWriteArrayList<>();
  private final Logger capturingLogger = new Logger() {
    @Override
    public void debug(Object message, Object... optionalParams) {
      StringBuilder line = new StringBuilder(String.valueOf(message));
      for (Object parameter : optionalParams) {
        line.append(parameter);
      }
      socketLog.add(line.toString());
    }

    @Override
    public void info(Object message, Object... optionalParams) {}

    @Override
    public void warn(Object message, Object... optionalParams) {}

    @Override
    public void error(Object message, Object... optionalParams) {}
  };

  @BeforeEach
  void start() {
    server = FakeGraphQLServer.start();
    graphql = GraphQLHelperHttpTest.helperFor(server, GraphQLClientOptions.builder()
        .socketLogger(capturingLogger)
        .connectionTimeout(Duration.ofSeconds(10))
        .build());
  }

  @AfterEach
  void stop() {
    clients.forEach(DisconnectableGraphQLClient::disconnect);
    server.close();
  }

  private DisconnectableGraphQLClient connect() {
    DisconnectableGraphQLClient client = graphql.clientWithSubscriptions();
    clients.add(client);
    return client;
  }

  private DisconnectableGraphQLClient connect(String token) {
    DisconnectableGraphQLClient client = graphql.clientWithSubscriptions(token);
    clients.add(client);
    return client;
  }

  private FakeSocket socket(int index) {
    await().atMost(WAIT).until(() -> server.sockets().size() > index);
    return server.sockets().get(index);
  }

  private static void awaitMessages(FakeSocket socket, int count) {
    await().atMost(WAIT).until(() -> socket.receivedTexts().size() >= count);
  }

  private static SubscriptionObservable cartSubscription(DisconnectableGraphQLClient client, String cartId) {
    return client.subscribe(CART_SUBSCRIPTION, Map.of("cartId", cartId));
  }

  @Nested
  @DisplayName("connecting")
  class Connecting {

    @Test
    @DisplayName("asks for the graphql-ws sub-protocol and sends connection_init with an empty payload when anonymous")
    void anonymous() {
      connect();

      FakeSocket socket = socket(0);
      assertThat(socket.requestedProtocols()).containsExactly("graphql-ws");
      assertThat(socket.receivedTexts().getFirst()).isEqualTo("{\"type\":\"connection_init\",\"payload\":{}}");
      assertThat(socket.headers()).doesNotContainKey("authorization");
    }

    @Test
    @DisplayName("sends the token as Authorization in the connection_init payload")
    void withToken() {
      connect("my-token");
      assertThat(socket(0).receivedTexts().getFirst())
          .isEqualTo("{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer my-token\"}}");
    }

    @Test
    @DisplayName("fails with the payload of connection_error and closes the socket")
    void connectionError() {
      server.onMessage((socket, message) -> {
        if ("connection_init".equals(message.get("type"))) {
          socket.send(Map.of("type", "connection_error", "payload", "Unauthorized"));
        }
      });

      SubscriptionConnectionException error =
          catchThrowableOfType(SubscriptionConnectionException.class, () -> graphql.clientWithSubscriptions("bad"));

      assertThat(error).hasMessage("Unauthorized");
      assertThat(error.payload()).isEqualTo("Unauthorized");
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> !socket.isOpen());
      assertThat(socket.receivedTexts()).last().isEqualTo("{\"type\":\"connection_terminate\",\"payload\":null}");
      Sleep.sleep(500);
      assertThat(server.sockets()).hasSize(1);
    }

    @Test
    @DisplayName("treats a connection_error with a falsy payload as a success, like the TypeScript callback")
    void falsyConnectionError() {
      server.onMessage((socket, message) -> {
        if ("connection_init".equals(message.get("type"))) {
          socket.sendText("{\"type\":\"connection_error\",\"payload\":\"\"}");
        }
      });
      assertThat(connect()).isNotNull();
    }

    @Test
    @DisplayName("times out when the connection is never acknowledged, closing the socket")
    void notAcknowledged() {
      server.onMessage((socket, message) -> {});
      GraphQLHelper impatient =
          graphql.withOptions(graphql.options().toBuilder().connectionTimeout(Duration.ofMillis(300)).build());

      assertThatThrownBy(impatient::clientWithSubscriptions)
          .isInstanceOf(SubscriptionConnectionException.class)
          .hasMessageStartingWith("Timed out after PT0.3S waiting for the GraphQL websocket " + server.websocketUrl());
      FakeSocket socket = socket(0);
      await().atMost(WAIT).until(() -> !socket.isOpen());
    }

    @Test
    @DisplayName("fails when the server does not select the graphql-ws sub-protocol")
    void noSubprotocol() {
      server.acceptSubprotocol(false);
      GraphQLHelper impatient =
          graphql.withOptions(graphql.options().toBuilder().connectionTimeout(Duration.ofMillis(800)).build());

      assertThatThrownBy(impatient::clientWithSubscriptions).isInstanceOf(SubscriptionConnectionException.class);
      assertThat(socketLog).contains("[GraphQL socket] on error: Server sent no subprotocol");
      assertThat(socketLog).doesNotContain("[GraphQL socket] on open");
    }

    @Test
    @DisplayName("logs every socket event like the TypeScript ws subclass")
    void socketLog() {
      DisconnectableGraphQLClient client = connect();
      client.disconnect();

      await().atMost(WAIT).until(() -> socketLog.stream().anyMatch(line -> line.startsWith("[GraphQL socket] on close: ")));
      assertThat(socketLog).startsWith("[GraphQL socket] on open", "[GraphQL socket] on message: {\"type\":\"connection_ack\"}");
      assertThat(socketLog).contains("[GraphQL socket] on close: 1000");
    }
  }

  @Nested
  @DisplayName("subscriptions")
  class Subscriptions {

    @Test
    @DisplayName("are started with Apollo's start message, __typename included")
    void startMessage() {
      DisconnectableGraphQLClient client = connect();

      cartSubscription(client, "c1");

      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);
      Map<String, Object> start = socket.messages().get(1);
      assertThat(start.keySet()).containsExactly("id", "type", "payload");
      assertThat(start).containsEntry("id", "1").containsEntry("type", "start");
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = (Map<String, Object>) start.get("payload");
      assertThat(payload.keySet()).containsExactly("variables", "extensions", "operationName", "query");
      assertThat(payload).containsEntry("variables", Map.of("cartId", "c1"))
          .containsEntry("extensions", Map.of())
          .containsEntry("operationName", "CartReadModel");
      assertThat(((String) payload.get("query")).replaceAll("\\s+", " ").trim()).isEqualTo(
          "subscription CartReadModel($cartId: ID!) { CartReadModel(id: $cartId) { id cartItems { productId quantity"
              + " __typename } __typename } }");
    }

    @Test
    @DisplayName("send every variable over the websocket, even unused ones")
    void unusedVariables() {
      DisconnectableGraphQLClient client = connect();
      client.subscribe(CART_SUBSCRIPTION, Map.of("cartId", "c1", "random", "variable"));
      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = (Map<String, Object>) socket.messages().get(1).get("payload");
      assertThat(payload.get("variables")).isEqualTo(Map.of("cartId", "c1", "random", "variable"));
    }

    @Test
    @DisplayName("deliver data messages to every observer of the same server-side subscription")
    void data() throws Exception {
      DisconnectableGraphQLClient client = connect();
      SubscriptionObservable observable = cartSubscription(client, "c1");
      List<Object> first = new CopyOnWriteArrayList<>();
      List<Object> second = new CopyOnWriteArrayList<>();
      observable.subscribe(result -> first.add(result.get("CartReadModel.id")));
      observable.subscribe(result -> second.add(result.get("CartReadModel.id")));

      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);
      socket.sendData("1", Map.of("CartReadModel", Map.of("id", "c1", "__typename", "CartReadModel")));

      await().atMost(WAIT).until(() -> first.size() == 1 && second.size() == 1);
      assertThat(first).containsExactly("c1");
      assertThat(socket.messages("start")).hasSize(1);
      // promisifyNextSubscriptionResult after the result arrived: the latest result is replayed
      GraphQLResponse replayed = observable.nextResult().get(5, TimeUnit.SECONDS);
      assertThat(replayed.data()).isEqualTo(Map.of("CartReadModel", Map.of("id", "c1", "__typename", "CartReadModel")));
    }

    @Test
    @DisplayName("each client.subscribe call starts its own operation with the next id")
    void ids() {
      DisconnectableGraphQLClient client = connect();
      cartSubscription(client, "c1").subscribe(result -> {});
      cartSubscription(client, "c2").subscribe(result -> {});

      FakeSocket socket = socket(0);
      awaitMessages(socket, 3);
      assertThat(socket.messages("start")).extracting(message -> message.get("id")).containsExactly("1", "2");
      assertThat(client.websocket().activeOperationIds()).containsExactly("1", "2");
    }

    @Test
    @DisplayName("are stopped when their last observer unsubscribes")
    void unsubscribe() {
      DisconnectableGraphQLClient client = connect();
      cartSubscription(client, "c1").subscribe(result -> {});
      SubscriptionObservable second = cartSubscription(client, "c2");
      ObservableSubscription a = second.subscribe(result -> {});
      ObservableSubscription b = second.subscribe(result -> {});

      a.unsubscribe();
      FakeSocket socket = socket(0);
      Sleep.sleep(200);
      assertThat(socket.messages("stop")).isEmpty();

      b.unsubscribe();
      b.unsubscribe();
      await().atMost(WAIT).until(() -> !socket.messages("stop").isEmpty());
      assertThat(socket.receivedTexts()).last().isEqualTo("{\"id\":\"2\",\"type\":\"stop\"}");
      assertThat(a.isClosed()).isTrue();
      assertThat(client.websocket().activeOperationIds()).containsExactly("1");

      // a stopped observable never restarts: new observers only get its completion
      List<String> events = new CopyOnWriteArrayList<>();
      second.subscribe(result -> events.add("next"), error -> events.add("error"), () -> events.add("complete"));
      Sleep.sleep(200);
      assertThat(events).isEmpty();
      assertThat(socket.messages("start")).hasSize(2);
    }

    @Test
    @DisplayName("report error messages to the observers with the first error's message, without sending stop")
    void errorMessage() {
      DisconnectableGraphQLClient client = connect();
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      cartSubscription(client, "c1").subscribe(result -> {}, errors::add);
      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);

      socket.send(Map.of("id", "1", "type", "error",
          "payload", Map.of("errors", List.of(Map.of("message", "Access denied for this resource"), Map.of("message", "x")))));

      await().atMost(WAIT).until(() -> errors.size() == 1);
      GraphQLClientException error = (GraphQLClientException) errors.getFirst();
      assertThat(error).hasMessage("Access denied for this resource");
      assertThat(error.graphQLErrors()).extracting(GraphQLError::message)
          .containsExactly("Access denied for this resource", "x");
      Sleep.sleep(200);
      assertThat(socket.messages("stop")).isEmpty();
      assertThat(client.websocket().activeOperationIds()).isEmpty();
    }

    @Test
    @DisplayName("fail the observer that receives a result with errors, stopping the operation when it was the last")
    void dataWithErrors() {
      DisconnectableGraphQLClient client = connect();
      List<Throwable> errors = new CopyOnWriteArrayList<>();
      cartSubscription(client, "c1").subscribe(result -> {}, errors::add);
      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);

      socket.send(Map.of("id", "1", "type", "data",
          "payload", Map.of("data", Map.of(), "errors", List.of(Map.of("message", "boom")))));

      await().atMost(WAIT).until(() -> errors.size() == 1);
      assertThat(errors.getFirst()).isInstanceOf(GraphQLClientException.class).hasMessage("boom");
      await().atMost(WAIT).until(() -> socket.messages("stop").size() == 1);
    }

    @Test
    @DisplayName("complete their observers on complete messages")
    void complete() {
      DisconnectableGraphQLClient client = connect();
      List<String> events = new CopyOnWriteArrayList<>();
      cartSubscription(client, "c1")
          .subscribe(result -> events.add("next"), error -> events.add("error"), () -> events.add("complete"));
      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);

      socket.sendData("1", Map.of("CartReadModel", Map.of("id", "c1")));
      socket.send(Map.of("id", "1", "type", "complete"));
      socket.sendData("1", Map.of("CartReadModel", Map.of("id", "ignored")));
      socket.sendData("99", Map.of("CartReadModel", Map.of("id", "unknown")));

      await().atMost(WAIT).until(() -> events.size() == 2);
      Sleep.sleep(200);
      assertThat(events).containsExactly("next", "complete");
      assertThat(socket.messages("stop")).isEmpty();
    }

    @Test
    @DisplayName("default observers report unhandled errors instead of losing them")
    void unhandledErrors() {
      DisconnectableGraphQLClient client = connect();
      SubscriptionObservable observable = cartSubscription(client, "c1");
      observable.subscribe(new SubscriptionObserver() {});
      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);
      socket.send(Map.of("id", "1", "type", "error", "payload", Map.of("message", "oops")));
      CompletableFuture<GraphQLResponse> next = observable.nextResult();
      await().atMost(WAIT).until(next::isDone);
      assertThatThrownBy(next::get).isInstanceOf(ExecutionException.class).hasRootCauseMessage("oops");
    }
  }

  @Nested
  @DisplayName("queries and mutations")
  class QueriesAndMutations {

    @Test
    @DisplayName("go over HTTP with the same token")
    void overHttp() {
      server.onHttp(request -> FakeResponse.json(200, "{\"data\":{\"ChangeCartItem\":true}}"));
      DisconnectableGraphQLClient client = connect("tok");

      GraphQLResponse response = client.mutate("mutation M { ChangeCartItem(input: {}) }");

      assertThat(response.get("ChangeCartItem")).isEqualTo(true);
      assertThat(server.requests()).hasSize(1);
      assertThat(server.requests().getFirst().headers()).containsEntry("authorization", "Bearer tok");
      assertThat(socket(0).messages("start")).isEmpty();
    }

    @Test
    @DisplayName("with a subscription document go over the websocket and wait for completion")
    void subscriptionDocument() throws Exception {
      server.onMessage((socket, message) -> {
        FakeGraphQLServer.defaultBehaviour(socket, message);
        if ("start".equals(message.get("type"))) {
          socket.sendData((String) message.get("id"), Map.of("value", 1));
          socket.sendData((String) message.get("id"), Map.of("value", 2));
          socket.send(Map.of("id", message.get("id"), "type", "complete"));
        }
      });
      DisconnectableGraphQLClient client = connect();

      GraphQLResponse response = client.query("subscription S { value }");

      assertThat(response.get("value")).isEqualTo(2);
      assertThat(server.requests()).isEmpty();
    }
  }

  @Nested
  @DisplayName("disconnecting and reconnecting")
  class Lifecycle {

    @Test
    @DisplayName("disconnect stops every subscription, terminates and closes the socket for good")
    void disconnect() {
      DisconnectableGraphQLClient client = connect();
      cartSubscription(client, "c1").subscribe(result -> {});
      cartSubscription(client, "c2").subscribe(result -> {});
      FakeSocket socket = socket(0);
      awaitMessages(socket, 3);

      client.disconnect();

      await().atMost(WAIT).until(() -> !socket.isOpen());
      assertThat(socket.receivedTexts()).endsWith(
          "{\"id\":\"1\",\"type\":\"stop\"}",
          "{\"id\":\"2\",\"type\":\"stop\"}",
          "{\"type\":\"connection_terminate\",\"payload\":null}");
      assertThat(socket.closeStatus()).isEqualTo(1000);
      Sleep.sleep(500);
      assertThat(server.sockets()).hasSize(1);
      assertThat(client.websocket().status()).isEqualTo(SubscriptionClient.Status.CLOSED);
    }

    @Test
    @DisplayName("close() of try-with-resources disconnects")
    void autoCloseable() {
      FakeSocket socket;
      try (DisconnectableGraphQLClient client = graphql.clientWithSubscriptions()) {
        assertThat(client.websocket().status()).isEqualTo(SubscriptionClient.Status.OPEN);
        socket = socket(0);
      }
      await().atMost(WAIT).until(() -> !socket.isOpen());
    }

    @Test
    @DisplayName("reconnect re-sends connection_init (re-evaluating the token) and every start with the same ids")
    void reconnect() throws Exception {
      AtomicInteger tokens = new AtomicInteger();
      DisconnectableGraphQLClient client = graphql.clientWithSubscriptions(() -> "token-" + tokens.incrementAndGet());
      clients.add(client);
      SubscriptionObservable first = cartSubscription(client, "c1");
      SubscriptionObservable second = cartSubscription(client, "c2");
      List<Object> received = new CopyOnWriteArrayList<>();
      first.subscribe(result -> received.add(result.get("CartReadModel.id")));
      second.subscribe(result -> received.add(result.get("CartReadModel.id")));
      FakeSocket original = socket(0);
      awaitMessages(original, 3);

      client.reconnect();

      FakeSocket renewed = socket(1);
      awaitMessages(renewed, 3);
      assertThat(original.isOpen()).isFalse();
      assertThat(renewed.receivedTexts().getFirst())
          .isEqualTo("{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer token-2\"}}");
      assertThat(renewed.messages("start")).isEqualTo(original.messages("start"));
      assertThat(original.messages("stop")).isEmpty();
      assertThat(original.messages("connection_terminate")).isEmpty();

      renewed.sendData("2", Map.of("CartReadModel", Map.of("id", "c2")));
      await().atMost(WAIT).until(() -> received.size() == 1);
      assertThat(received).containsExactly("c2");
    }

    @Test
    @DisplayName("several clients can reconnect concurrently")
    void concurrentReconnect() {
      DisconnectableGraphQLClient a = connect();
      DisconnectableGraphQLClient b = connect();
      CompletableFuture.allOf(a.reconnectAsync(), b.reconnectAsync()).orTimeout(15, TimeUnit.SECONDS).join();
      assertThat(server.sockets()).hasSize(4);
      await().atMost(WAIT).until(() -> server.openSockets().size() == 2);
      assertThat(server.sockets().subList(0, 2)).noneMatch(FakeSocket::isOpen);
    }

    @Test
    @DisplayName("reconnects automatically when the server closes or drops the socket")
    void serverClose() {
      DisconnectableGraphQLClient client = connect();
      AtomicInteger reconnections = new AtomicInteger();
      client.websocket().onReconnected(payload -> reconnections.incrementAndGet());
      cartSubscription(client, "c1").subscribe(result -> {});
      FakeSocket first = socket(0);
      awaitMessages(first, 2);

      first.close(1001, "going away");
      FakeSocket second = socket(1);
      awaitMessages(second, 2);
      assertThat(second.types()).containsExactly("connection_init", "start");
      await().atMost(WAIT).until(() -> reconnections.get() == 1);

      second.drop();
      FakeSocket third = socket(2);
      awaitMessages(third, 2);
      assertThat(third.messages("start")).isEqualTo(first.messages("start"));
      await().atMost(WAIT).until(() -> reconnections.get() == 2);
    }

    @Test
    @DisplayName("keeps retrying while the server is down and resumes when it is back")
    void serverDown() {
      DisconnectableGraphQLClient client = connect();
      cartSubscription(client, "c1").subscribe(result -> {});
      awaitMessages(socket(0), 2);

      server.stopServer();
      await().atMost(WAIT).until(() -> client.websocket().status() != SubscriptionClient.Status.OPEN);
      Sleep.sleep(700);
      server.startServer();

      FakeSocket renewed = socket(1);
      awaitMessages(renewed, 2);
      assertThat(renewed.types()).containsExactly("connection_init", "start");
    }

    @Test
    @DisplayName("disconnect while reconnecting cancels the pending reconnection")
    void disconnectWhileReconnecting() {
      DisconnectableGraphQLClient client = connect();
      server.stopServer();
      await().atMost(WAIT).until(() -> client.websocket().status() != SubscriptionClient.Status.OPEN);

      client.disconnect();
      server.startServer();
      Sleep.sleep(2500);

      assertThat(server.sockets()).hasSize(1);
    }

    @Test
    @DisplayName("a subscription after disconnect opens a new socket, like the JavaScript client")
    void subscribeAfterDisconnect() {
      DisconnectableGraphQLClient client = connect();
      client.disconnect();

      cartSubscription(client, "c1").subscribe(result -> {});

      FakeSocket renewed = socket(1);
      awaitMessages(renewed, 2);
      assertThat(renewed.types()).containsExactly("connection_init", "start");
      assertThat(renewed.messages("start").getFirst()).containsEntry("id", "1");
    }

    @Test
    @DisplayName("reconnect cannot block inside a subscription callback")
    void reconnectFromCallback() throws Exception {
      DisconnectableGraphQLClient client = connect();
      CompletableFuture<Throwable> failure = new CompletableFuture<>();
      cartSubscription(client, "c1").subscribe(result -> {
        try {
          client.reconnect();
          failure.complete(null);
        } catch (IllegalStateException e) {
          failure.complete(e);
        }
      });
      FakeSocket socket = socket(0);
      awaitMessages(socket, 2);
      socket.sendData("1", Map.of("CartReadModel", Map.of("id", "c1")));
      assertThat(failure.get(10, TimeUnit.SECONDS)).hasMessageContaining("reconnectAsync()");
    }
  }
}
