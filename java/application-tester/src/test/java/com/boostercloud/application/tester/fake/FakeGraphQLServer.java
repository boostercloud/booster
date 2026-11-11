package com.boostercloud.application.tester.fake;

import com.boostercloud.framework.types.BoosterJson;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

/**
 * An in-JVM fake of a Booster GraphQL endpoint: {@code POST /graphql} answered by a scriptable handler, and
 * {@code /websocket} speaking the server side of the {@code graphql-ws} protocol as far as the tests need (it records
 * every message, acknowledges {@code connection_init} unless told otherwise, and lets the test push messages, close
 * or drop sockets). Built on Jetty 12. The server can be stopped and started again on the same port.
 */
public final class FakeGraphQLServer implements AutoCloseable {

  /** A recorded HTTP request. */
  public record RecordedRequest(String method, String path, Map<String, String> headers, String body) {
    /**
     * @return the body parsed as a JSON object
     */
    public Map<String, Object> json() {
      return BoosterJson.parseObject(body);
    }
  }

  /** A scripted HTTP response. */
  public record FakeResponse(int status, String contentType, String body) {
    /**
     * @param status the status
     * @param json the JSON body text
     * @return a JSON response
     */
    public static FakeResponse json(int status, String json) {
      return new FakeResponse(status, "application/json", json);
    }
  }

  private final int port;
  private Server server;
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private final List<FakeSocket> sockets = new CopyOnWriteArrayList<>();
  private volatile Function<RecordedRequest, FakeResponse> httpHandler =
      request -> FakeResponse.json(200, "{\"data\":{}}");
  private volatile BiConsumer<FakeSocket, Map<String, Object>> messageHandler = FakeGraphQLServer::defaultBehaviour;
  private volatile boolean acceptSubprotocol = true;

  private FakeGraphQLServer(int port) {
    this.port = port;
  }

  /**
   * @return a started server on a free port
   */
  public static FakeGraphQLServer start() {
    FakeGraphQLServer fake = new FakeGraphQLServer(freePort());
    fake.startServer();
    return fake;
  }

  /**
   * @return a free local port (nothing listens there)
   */
  public static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Starts (again) on the same port. */
  public synchronized void startServer() {
    if (server != null) {
      return;
    }
    Server jetty = new Server();
    ServerConnector connector = new ServerConnector(jetty);
    connector.setHost("127.0.0.1");
    connector.setPort(port);
    jetty.addConnector(connector);
    WebSocketUpgradeHandler websocket = WebSocketUpgradeHandler.from(jetty, container -> {
      container.setIdleTimeout(Duration.ofMinutes(5));
      container.addMapping("/websocket", (request, response, callback) -> {
        List<String> protocols = request.getSubProtocols();
        if (acceptSubprotocol && protocols.contains("graphql-ws")) {
          response.setAcceptedSubProtocol("graphql-ws");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (HttpField field : request.getHeaders()) {
          headers.computeIfAbsent(field.getLowerCaseName(), ignored -> new ArrayList<>()).add(field.getValue());
        }
        FakeSocket socket = new FakeSocket(this, protocols, headers);
        sockets.add(socket);
        return socket;
      });
    });
    websocket.setHandler(new HttpHandler());
    jetty.setHandler(websocket);
    try {
      jetty.start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    server = jetty;
  }

  /** Stops the server (open sockets are closed). */
  public synchronized void stopServer() {
    if (server == null) {
      return;
    }
    try {
      server.stop();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    server = null;
  }

  @Override
  public void close() {
    stopServer();
  }

  /**
   * @return {@code http://127.0.0.1:<port>/graphql}
   */
  public String graphqlUrl() {
    return "http://127.0.0.1:" + port + "/graphql";
  }

  /**
   * @return {@code ws://127.0.0.1:<port>/websocket}
   */
  public String websocketUrl() {
    return "ws://127.0.0.1:" + port + "/websocket";
  }

  /**
   * @return {@code http://127.0.0.1:<port>/sensor/health/}
   */
  public String healthUrl() {
    return "http://127.0.0.1:" + port + "/sensor/health/";
  }

  /**
   * @param handler answers every HTTP request
   */
  public void onHttp(Function<RecordedRequest, FakeResponse> handler) {
    this.httpHandler = handler;
  }

  /**
   * @param handler handles every websocket message (replaces the default behaviour; call
   *     {@link #defaultBehaviour(FakeSocket, Map)} to keep it)
   */
  public void onMessage(BiConsumer<FakeSocket, Map<String, Object>> handler) {
    this.messageHandler = handler;
  }

  /**
   * @param accept whether the upgrade response selects the {@code graphql-ws} sub-protocol
   */
  public void acceptSubprotocol(boolean accept) {
    this.acceptSubprotocol = accept;
  }

  /**
   * @return the HTTP requests received so far
   */
  public List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  /**
   * @return the websockets accepted so far, in order
   */
  public List<FakeSocket> sockets() {
    return List.copyOf(sockets);
  }

  /**
   * @return the websockets that are open
   */
  public List<FakeSocket> openSockets() {
    return sockets.stream().filter(FakeSocket::isOpen).toList();
  }

  /**
   * The default server behaviour: acknowledge {@code connection_init}.
   *
   * @param socket the socket
   * @param message the received message
   */
  public static void defaultBehaviour(FakeSocket socket, Map<String, Object> message) {
    if ("connection_init".equals(message.get("type"))) {
      socket.send(Map.of("type", "connection_ack"));
    }
  }

  void received(FakeSocket socket, Map<String, Object> message) {
    messageHandler.accept(socket, message);
  }

  /** One accepted websocket. */
  public static final class FakeSocket implements Session.Listener.AutoDemanding {
    private final FakeGraphQLServer server;
    private final List<String> requestedProtocols;
    private final Map<String, List<String>> headers;
    private final List<String> received = new CopyOnWriteArrayList<>();
    private volatile Session session;
    private volatile Integer closeStatus;

    FakeSocket(FakeGraphQLServer server, List<String> requestedProtocols, Map<String, List<String>> headers) {
      this.server = server;
      this.requestedProtocols = List.copyOf(requestedProtocols);
      this.headers = Map.copyOf(headers);
    }

    @Override
    public void onWebSocketOpen(Session newSession) {
      this.session = newSession;
    }

    @Override
    public void onWebSocketText(String message) {
      received.add(message);
      server.received(this, BoosterJson.parseObject(message));
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      closeStatus = statusCode;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
      if (closeStatus == null) {
        closeStatus = -1;
      }
    }

    /**
     * @return the sub-protocols the client requested
     */
    public List<String> requestedProtocols() {
      return requestedProtocols;
    }

    /**
     * @return the upgrade request headers
     */
    public Map<String, List<String>> headers() {
      return headers;
    }

    /**
     * @return the raw texts received, in order
     */
    public List<String> receivedTexts() {
      return List.copyOf(received);
    }

    /**
     * @return the messages received, parsed, in order
     */
    public List<Map<String, Object>> messages() {
      List<Map<String, Object>> messages = new ArrayList<>();
      for (String text : received) {
        messages.add(BoosterJson.parseObject(text));
      }
      return messages;
    }

    /**
     * @param type a message type
     * @return the received messages of that type
     */
    public List<Map<String, Object>> messages(String type) {
      return messages().stream().filter(message -> type.equals(message.get("type"))).toList();
    }

    /**
     * @return the message types received, in order
     */
    public List<Object> types() {
      return messages().stream().map(message -> message.get("type")).toList();
    }

    /**
     * @param message a message (serialised as JSON)
     */
    public synchronized void send(Object message) {
      sendText(BoosterJson.toJson(message));
    }

    /**
     * @param text raw text to send
     */
    public synchronized void sendText(String text) {
      Session current = session;
      if (current != null && current.isOpen()) {
        current.sendText(text, org.eclipse.jetty.websocket.api.Callback.NOOP);
      }
    }

    /**
     * @param id the operation id
     * @param data the {@code data} object
     */
    public void sendData(String id, Map<String, Object> data) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("data", data);
      Map<String, Object> message = new LinkedHashMap<>();
      message.put("id", id);
      message.put("type", "data");
      message.put("payload", payload);
      send(message);
    }

    /**
     * Closes the socket with a close frame.
     *
     * @param status the close status
     * @param reason the reason
     */
    public void close(int status, String reason) {
      Session current = session;
      if (current != null) {
        current.close(status, reason, org.eclipse.jetty.websocket.api.Callback.NOOP);
      }
    }

    /** Drops the TCP connection without a close frame. */
    public void drop() {
      Session current = session;
      if (current != null) {
        current.disconnect();
      }
    }

    /**
     * @return whether the socket is open
     */
    public boolean isOpen() {
      Session current = session;
      return current != null && current.isOpen() && closeStatus == null;
    }

    /**
     * @return the close status (or {@code -1} for errors), {@code null} while open
     */
    public Integer closeStatus() {
      return closeStatus;
    }
  }

  private final class HttpHandler extends Handler.Abstract {
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
      String body = Content.Source.asString(request, StandardCharsets.UTF_8);
      Map<String, String> headers = new LinkedHashMap<>();
      for (HttpField field : request.getHeaders()) {
        headers.put(field.getLowerCaseName(), field.getValue());
      }
      RecordedRequest recorded =
          new RecordedRequest(request.getMethod(), Request.getPathInContext(request), headers, body);
      requests.add(recorded);
      FakeResponse fakeResponse = httpHandler.apply(recorded);
      response.setStatus(fakeResponse.status());
      if (fakeResponse.contentType() != null) {
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, fakeResponse.contentType());
      }
      Content.Sink.write(response, true, fakeResponse.body(), callback);
      return true;
    }
  }
}
