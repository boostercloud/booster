package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.library.ExpressWebSocketMessage;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EventType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The GraphQL websocket endpoint the local server starts with the provider: {@code ws://localhost:<port>/websocket},
 * sub-protocol {@code graphql-ws}, CONNECT / MESSAGE / DISCONNECT events handed to the application.
 */
class WebSocketTest {

  @TempDir
  Path directory;

  private ServerFixture fixture;
  private FakeUserApp app;
  private LocalServer server;
  private final List<ExpressWebSocketMessage> events = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() {
    fixture = new ServerFixture();
    app = new FakeUserApp(fixture.config);
    app.onGraphQL(request -> {
      ExpressWebSocketMessage message = (ExpressWebSocketMessage) request;
      events.add(message);
      String connectionId = message.connectionContext().connectionId().toString();
      if (message.connectionContext().eventType() == EventType.CONNECT) {
        app.provider().connections().storeData(fixture.config, connectionId,
            new ConnectionDataEnvelope(4102444800L, null));
      } else if (message.connectionContext().eventType() == EventType.MESSAGE
          && message.data() instanceof Map<?, ?> data && "connection_init".equals(data.get("type"))) {
        app.provider().connections().sendMessage(fixture.config, connectionId, Map.of("type", "connection_ack"));
      } else if (message.connectionContext().eventType() == EventType.DISCONNECT) {
        app.provider().connections().deleteData(fixture.config, connectionId);
      }
      return ApiAdapter.requestSucceeded(null, Map.of());
    });
    server = fixture.start(directory, app);
  }

  @AfterEach
  void tearDown() {
    fixture.close();
  }

  @Test
  void connectMessageAndDisconnectReachTheApplication() throws Exception {
    BlockingQueue<String> received = new LinkedBlockingQueue<>();
    WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
        .subprotocols("graphql-ws")
        .header("Authorization", "Bearer ws-token")
        .buildAsync(URI.create(server.websocketURL()), new WebSocket.Listener() {
          @Override
          public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            received.add(data.toString());
            webSocket.request(1);
            return null;
          }
        })
        .get(10, TimeUnit.SECONDS);
    assertThat(socket.getSubprotocol()).isEqualTo("graphql-ws");

    await().until(() -> events.size() == 1);
    ExpressWebSocketMessage connect = events.get(0);
    assertThat(connect.connectionContext().eventType()).isEqualTo(EventType.CONNECT);
    assertThat(connect.incomingMessage().url()).isEqualTo("/websocket");
    assertThat(connect.incomingMessage().authorization()).isEqualTo("Bearer ws-token");

    socket.sendText(BoosterJson.toJson(Map.of("type", "connection_init", "payload", Map.of())), true).get();
    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"connection_ack\"}");
    ExpressWebSocketMessage message = events.get(1);
    assertThat(message.connectionContext().eventType()).isEqualTo(EventType.MESSAGE);
    assertThat(message.connectionContext().connectionId()).isEqualTo(connect.connectionContext().connectionId());

    LocalTestHelper helper = LocalTestHelper.build("my-store", LocalTestHelper.Options.defaults()
        .withBaseDirectory(directory).withPorts(server.port(), server.runtime().webSocketServerAdapter().port())
        .withReadinessTimeout(null));
    assertThat(helper.outputs().websocketURL()).isEqualTo(server.websocketURL());
    assertThat(helper.counters().connections()).isEqualTo(1);

    socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get();
    await().until(() -> events.size() == 3);
    assertThat(events.get(2).connectionContext().eventType()).isEqualTo(EventType.DISCONNECT);
    await().until(() -> helper.counters().connections() == 0);
  }
}
