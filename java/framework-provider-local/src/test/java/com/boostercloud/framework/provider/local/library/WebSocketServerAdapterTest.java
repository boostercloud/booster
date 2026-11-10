package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.graphql.GraphQLInitAck;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The Jetty websocket server, driven with the JDK websocket client (not covered by the TypeScript tests). */
class WebSocketServerAdapterTest {

  private final List<ExpressWebSocketMessage> received = Collections.synchronizedList(new ArrayList<>());
  private final HttpClient client = HttpClient.newHttpClient();
  private BoosterConfig config;
  private WebSocketServerAdapter server;
  private volatile CountDownLatch connectGate = new CountDownLatch(0);
  private volatile long disconnectMillis;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
    UserApp app = Mockito.mock(UserApp.class);
    Mockito.when(app.boosterServeGraphQL(Mockito.any())).thenAnswer(invocation -> {
      ExpressWebSocketMessage message = invocation.getArgument(0);
      if (message.connectionContext().eventType() == EventType.CONNECT) {
        connectGate.await(5, TimeUnit.SECONDS);
      }
      if (message.connectionContext().eventType() == EventType.DISCONNECT && disconnectMillis > 0) {
        Thread.sleep(disconnectMillis);
      }
      received.add(message);
      if (message.data() instanceof java.util.Map<?, ?> data && "boom".equals(data.get("type"))) {
        throw new IllegalStateException("handler failed");
      }
      return null;
    });
    server = new WebSocketServerAdapter(new GraphQLService(app), () -> config, "0", true);
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  /** Collects the text frames a client receives. */
  private static final class Listener implements WebSocket.Listener {
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final StringBuilder partial = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        messages.add(partial.toString());
        partial.setLength(0);
      }
      webSocket.request(1);
      return null;
    }
  }

  private WebSocket connect(Listener listener, String... protocols) throws Exception {
    WebSocket.Builder builder = client.newWebSocketBuilder().header("Authorization", "Bearer token");
    if (protocols.length > 0) {
      builder.subprotocols(protocols[0], java.util.Arrays.copyOfRange(protocols, 1, protocols.length));
    }
    return builder.buildAsync(URI.create("ws://localhost:" + server.port() + "/websocket"), listener)
        .get(5, TimeUnit.SECONDS);
  }

  @Test
  void dispatchesConnectMessagesAndDisconnectInOrder() throws Exception {
    assertThat(server.isRunning()).isTrue();
    assertThat(server.port()).isPositive();
    Listener listener = new Listener();
    connectGate = new CountDownLatch(1);
    WebSocket socket = connect(listener, "graphql-ws");
    assertThat(socket.getSubprotocol()).isEqualTo("graphql-ws");
    // frames sent while the CONNECT dispatch is still running are queued, not lost
    socket.sendText("{\"type\":\"connection_init\",\"payload\":{}}", true).get(5, TimeUnit.SECONDS);
    socket.sendText("not json", true).get(5, TimeUnit.SECONDS);
    socket.sendBinary(ByteBuffer.wrap("{\"type\":\"start\",\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8)), true)
        .get(5, TimeUnit.SECONDS);
    Thread.sleep(100);
    connectGate.countDown();
    await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 3);
    assertThat(received).extracting(m -> m.connectionContext().eventType())
        .containsExactly(EventType.CONNECT, EventType.MESSAGE, EventType.MESSAGE);
    ExpressWebSocketMessage connectMessage = received.get(0);
    assertThat(connectMessage.incomingMessage().authorization()).isEqualTo("Bearer token");
    assertThat(connectMessage.incomingMessage().url()).isEqualTo("/websocket");
    assertThat(connectMessage.data()).isNull();
    assertThat(received.get(1).data()).isEqualTo(map("type", "connection_init", "payload", map()));
    assertThat(received.get(2).data()).isEqualTo(map("type", "start", "id", "1"));
    String connectionId = connectMessage.connectionContext().connectionId().value();
    assertThat(received).allMatch(m -> m.connectionContext().connectionId().value().equals(connectionId));
    Mockito.verify(config.logger).error(Mockito.eq("[Booster]|web-socket-server-adapter#onMessage: "),
        Mockito.eq("Error parsing websocket message"), Mockito.any());
    assertThat(server.connectionIds()).containsExactly(connectionId);

    server.sendToConnection(connectionId, new GraphQLInitAck());
    server.sendToConnection(connectionId, map("type", "data", "id", "1", "payload", map("data", map("x", 1))));
    await().atMost(Duration.ofSeconds(5)).until(() -> listener.messages.size() == 2);
    assertThat(listener.messages).containsExactly("{\"type\":\"connection_ack\"}",
        "{\"type\":\"data\",\"id\":\"1\",\"payload\":{\"data\":{\"x\":1}}}");

    socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 4);
    ExpressWebSocketMessage disconnect = received.get(3);
    assertThat(disconnect.connectionContext().eventType()).isEqualTo(EventType.DISCONNECT);
    assertThat(disconnect.incomingMessage()).isNull();
    await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionIds().isEmpty());
    assertThatThrownBy(() -> server.sendToConnection(connectionId, map()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void closingTheServerLetsTheOpenConnectionsProcessTheirDisconnectFirst() throws Exception {
    // core's DISCONNECT handling deletes the connection data and subscriptions (flushed writes); the runtime closes
    // the data files right after the websocket server, so close() must not return (or interrupt it) before
    disconnectMillis = 300;
    WebSocket first = connect(new Listener(), "graphql-ws");
    WebSocket second = connect(new Listener(), "graphql-ws");
    await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 2);

    server.close();

    assertThat(received).extracting(m -> m.connectionContext().eventType())
        .containsExactly(EventType.CONNECT, EventType.CONNECT, EventType.DISCONNECT, EventType.DISCONNECT);
    assertThat(server.connectionIds()).isEmpty();
    assertThat(server.isRunning()).isFalse();
    first.abort();
    second.abort();
  }

  @Test
  void connectionsAreIndependentAndHandlerFailuresAreLogged() throws Exception {
    Listener first = new Listener();
    Listener second = new Listener();
    WebSocket a = connect(first);
    WebSocket b = connect(second);
    assertThat(a.getSubprotocol()).isEmpty();
    await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionIds().size() == 2);
    a.sendText("{\"type\":\"boom\"}", true).get(5, TimeUnit.SECONDS);
    a.sendText("{\"type\":\"after\"}", true).get(5, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 4);
    Mockito.verify(config.logger).error(Mockito.eq("[Booster]|web-socket-server-adapter#onMessage: "),
        Mockito.startsWith("Error handling the websocket MESSAGE event"), Mockito.any(IllegalStateException.class));
    for (String id : server.connectionIds()) {
      server.sendToConnection(id, map("to", id));
    }
    await().atMost(Duration.ofSeconds(5)).until(() -> first.messages.size() == 1 && second.messages.size() == 1);
    assertThat(first.messages.get(0)).isNotEqualTo(second.messages.get(0));
    a.abort();
    b.abort();
    await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionIds().isEmpty());
  }

  @Test
  void onlyTheWebSocketPathUpgrades() throws Exception {
    CompletableFuture<WebSocket> wrongPath = client.newWebSocketBuilder()
        .buildAsync(URI.create("ws://localhost:" + server.port() + "/other"), new Listener());
    assertThatThrownBy(() -> wrongPath.get(5, TimeUnit.SECONDS))
        .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class)
        .rootCause().hasMessageContaining("404");
    HttpResponse<String> plain = client.send(java.net.http.HttpRequest.newBuilder(
        URI.create("http://localhost:" + server.port() + "/websocket")).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(plain.statusCode()).isGreaterThanOrEqualTo(400);
  }

  @Test
  void aPortInUseIsReportedWithoutCrashing() {
    WebSocketServerAdapter second = new WebSocketServerAdapter(new GraphQLService(Mockito.mock(UserApp.class)),
        () -> config, Integer.toString(server.port()), true);
    try {
      assertThat(second.isRunning()).isFalse();
      assertThat(second.port()).isEqualTo(-1);
      Mockito.verify(config.logger).error(Mockito.eq("[Booster]|WebSocketServerAdapter#constructor#handleUpgrade: "),
          Mockito.startsWith("Could not start the websocket server on port " + server.port()));
      Mockito.verify(config.logger, Mockito.atLeastOnce()).info("[Booster]|WebSocketServerAdapter#constructor#handleUpgrade: ",
          "Starting websocket server on ws://localhost:" + server.port() + "/websocket");
    } finally {
      second.close();
    }
  }
}
