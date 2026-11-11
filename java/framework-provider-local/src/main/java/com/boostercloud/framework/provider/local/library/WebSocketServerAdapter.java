package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

/**
 * The websocket server of the local provider (Jetty): {@code ws://localhost:<port>/websocket}, with the port from
 * {@code LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT} (default {@code 65529}). Port of
 * {@code library/web-socket-server-adapter.ts}.
 *
 * <p>Every connection gets a server-generated UUID. Its events reach core through
 * {@code GraphQLService.handleGraphQLRequest} as {@link ExpressWebSocketMessage}s: {@code CONNECT} when it opens,
 * {@code MESSAGE} for every text (or binary, decoded as UTF-8) frame holding JSON, {@code DISCONNECT} when it
 * closes. Answers travel only through {@link #sendToConnection(String, Object)}, called by core via
 * {@code connections.sendMessage}. The first sub-protocol offered by the client (normally {@code graphql-ws}) is
 * accepted, like the {@code ws} library does.
 *
 * <p>Differences from TypeScript, all fixes: the events of one connection are processed one at a time, in order, on a
 * virtual thread (TypeScript processes messages concurrently and loses the frames that arrive before the
 * {@code CONNECT} dispatch finished); failures of a dispatch are logged instead of becoming unhandled rejections;
 * closed connections are removed from the connection table; failing to bind the port is logged instead of crashing
 * the process; the server threads are daemon threads, so the websocket server alone does not keep the JVM alive; there
 * is no idle timeout (like {@code ws}) and messages up to 100 MiB are accepted ({@code ws}'s default limit).
 */
public class WebSocketServerAdapter implements AutoCloseable {

  /** Environment variable with the websocket port. */
  public static final String LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT = "LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT";

  /** Default websocket port. */
  public static final String DEFAULT_PORT = "65529";

  /** Path of the websocket endpoint. */
  public static final String PATH = "/websocket";

  /** How long {@link #close()} lets the connections finish their pending events, {@code DISCONNECT} included. */
  public static final Duration CLOSE_DRAIN_TIMEOUT = Duration.ofSeconds(5);

  private static final long MAX_MESSAGE_SIZE = 100L * 1024 * 1024;

  private final GraphQLService graphQLService;
  private final Supplier<BoosterConfig> config;
  private final String wsPort;
  private final Map<String, Connection> clients = new ConcurrentHashMap<>();
  private Server server;
  private int boundPort = -1;

  /**
   * {@code new WebSocketServerAdapter(graphQLService, config)}: starts listening right away, like TypeScript.
   *
   * @param graphQLService the GraphQL service
   * @param config the configuration (used for logging)
   */
  public WebSocketServerAdapter(GraphQLService graphQLService, BoosterConfig config) {
    this(graphQLService, () -> config, EnvironmentVariables.get(LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT)
        .orElse(DEFAULT_PORT), true);
  }

  /**
   * @param graphQLService the GraphQL service
   * @param config resolves the configuration when logging
   * @param port the port ({@code "0"} picks a free one)
   * @param start whether to start listening now
   */
  public WebSocketServerAdapter(GraphQLService graphQLService, Supplier<BoosterConfig> config, String port,
      boolean start) {
    this.graphQLService = Objects.requireNonNull(graphQLService, "graphQLService");
    this.config = Objects.requireNonNull(config, "config");
    this.wsPort = port;
    if (start) {
      attach();
    }
  }

  private Logger logger(String location) {
    return Loggers.getLogger(config.get(), location);
  }

  /**
   * {@code attach()}: starts the server (no-op when already started).
   */
  public synchronized void attach() {
    if (server != null) {
      return;
    }
    Logger logger = logger("WebSocketServerAdapter#constructor#handleUpgrade");
    logger.info("Starting websocket server on ws://localhost:" + wsPort + PATH);
    JettyLogging.prepare();
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setName("booster-websocket");
    threadPool.setDaemon(true);
    Server newServer = new Server(threadPool);
    ServerConnector connector = new ServerConnector(newServer);
    try {
      connector.setPort(Integer.parseInt(wsPort.trim()));
      newServer.addConnector(connector);
      ContextHandler context = new ContextHandler("/");
      WebSocketUpgradeHandler upgradeHandler = WebSocketUpgradeHandler.from(newServer, context, container -> {
        container.setIdleTimeout(Duration.ofDays(3650));
        container.setMaxTextMessageSize(MAX_MESSAGE_SIZE);
        container.setMaxBinaryMessageSize(MAX_MESSAGE_SIZE);
        container.addMapping(PATH, (upgradeRequest, upgradeResponse, callback) -> {
          List<String> protocols = upgradeRequest.getSubProtocols();
          if (protocols != null && !protocols.isEmpty()) {
            upgradeResponse.setAcceptedSubProtocol(protocols.get(0));
          }
          return new Connection(toLocalHttpRequest(upgradeRequest));
        });
      });
      context.setHandler(upgradeHandler);
      newServer.setHandler(context);
      newServer.start();
      server = newServer;
      boundPort = connector.getLocalPort();
    } catch (Exception e) {
      logger.error("Could not start the websocket server on port " + wsPort + ": " + e.getMessage());
      try {
        newServer.stop();
      } catch (Exception ignored) {
        // nothing was started
      }
    }
  }

  /**
   * @return the port the server listens on, or {@code -1} when it is not running
   */
  public synchronized int port() {
    return boundPort;
  }

  /**
   * @return whether the server is listening
   */
  public synchronized boolean isRunning() {
    return server != null && server.isRunning();
  }

  /**
   * @return the ids of the open connections
   */
  public Set<String> connectionIds() {
    return Set.copyOf(clients.keySet());
  }

  /**
   * Whether a connection is open in this process. A connection is registered before its {@code CONNECT} is processed
   * and unregistered after its {@code DISCONNECT} was, so every connection or subscription row stored for an open
   * connection belongs to a registered one.
   *
   * @param connectionId a connection id
   * @return whether that connection is open
   */
  public boolean hasConnection(String connectionId) {
    return connectionId != null && clients.containsKey(connectionId);
  }

  /**
   * {@code sendToConnection(connectionId, data)}: sends {@code JSON.stringify(data)} as one text frame. Sending to a
   * connection that is closing is silently dropped, as {@code ws} does.
   *
   * @param connectionId the connection id
   * @param data the message
   * @throws IllegalStateException when there is no such connection (TypeScript fails with a {@code TypeError})
   */
  public void sendToConnection(String connectionId, Object data) {
    String stringData = BoosterJson.toJson(data);
    Connection connection = clients.get(connectionId);
    if (connection == null) {
      throw new IllegalStateException("There is no open websocket connection with id " + connectionId);
    }
    connection.send(stringData);
  }

  /**
   * Stops the server and drops every connection. Stopping the server closes the open connections, whose
   * {@code DISCONNECT} events (core deletes the connection data and its subscriptions) are processed before this
   * returns, for up to {@link #CLOSE_DRAIN_TIMEOUT}; whatever is still running then is interrupted. The data files
   * must therefore be closed after the websocket server, as {@code LocalProviderRuntime.close()} does.
   */
  @Override
  public synchronized void close() {
    if (server != null) {
      try {
        server.stop();
      } catch (Exception e) {
        logger("WebSocketServerAdapter#close").warn("Error stopping the websocket server", e);
      }
      server = null;
      boundPort = -1;
    }
    long deadline = System.nanoTime() + CLOSE_DRAIN_TIMEOUT.toNanos();
    for (Connection connection : clients.values()) {
      connection.executor.shutdown();
      try {
        if (!connection.executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
          connection.executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        connection.executor.shutdownNow();
      }
    }
    clients.clear();
  }


  private static LocalHttpRequest toLocalHttpRequest(ServerUpgradeRequest request) {
    Map<String, String> headers = new LinkedHashMap<>();
    for (HttpField field : request.getHeaders()) {
      headers.merge(field.getName().toLowerCase(java.util.Locale.ROOT), field.getValue(), (a, b) -> a + ", " + b);
    }
    return LocalHttpRequest.of(request.getMethod(), request.getHttpURI().getPathQuery(), headers, null);
  }

  /**
   * One websocket connection: its session, upgrade request and serial event queue. Public only because Jetty binds the
   * listener methods with a public method lookup; it is not meant to be used directly.
   */
  public final class Connection implements Session.Listener.AutoDemanding {
    private final LocalHttpRequest upgradeRequest;
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name("booster-websocket-connection").factory());
    private volatile Session session;
    private volatile UUID connectionId;

    Connection(LocalHttpRequest upgradeRequest) {
      this.upgradeRequest = upgradeRequest;
    }

    @Override
    public void onWebSocketOpen(Session newSession) {
      this.session = newSession;
      this.connectionId = UUID.generate();
      clients.put(connectionId.toString(), this);
      ExpressWebSocketMessage request =
          new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.CONNECT), upgradeRequest, null);
      dispatch(request);
    }

    @Override
    public void onWebSocketText(String message) {
      enqueue(() -> handleMessage(message));
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
      String message = StandardCharsets.UTF_8.decode(payload).toString();
      callback.succeed();
      enqueue(() -> handleMessage(message));
    }

    private void enqueue(Runnable task) {
      try {
        executor.execute(task);
      } catch (java.util.concurrent.RejectedExecutionException e) {
        // the connection is closing or the server stopped: the frame is dropped, as ws does after close
      }
    }

    private void handleMessage(String message) {
      Logger logger = logger("web-socket-server-adapter#onMessage");
      Object data;
      try {
        data = BoosterJson.parse(message);
      } catch (RuntimeException e) {
        logger.error("Error parsing websocket message", e);
        return;
      }
      process(new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.MESSAGE), upgradeRequest, data));
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      UUID id = connectionId;
      if (id == null) {
        return;
      }
      try {
        executor.execute(() -> {
          try {
            process(new ExpressWebSocketMessage(new ConnectionContext(id, EventType.DISCONNECT), null, null));
          } finally {
            clients.remove(id.toString(), this);
          }
        });
      } catch (java.util.concurrent.RejectedExecutionException e) {
        clients.remove(id.toString(), this);
      }
      executor.shutdown();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
      logger("web-socket-server-adapter#onError").debug("Websocket error on connection " + connectionId, cause);
    }

    private void dispatch(ExpressWebSocketMessage request) {
      enqueue(() -> process(request));
    }

    private void process(ExpressWebSocketMessage request) {
      try {
        graphQLService.handleGraphQLRequest(request);
      } catch (RuntimeException | Error e) {
        logger("web-socket-server-adapter#onMessage").error("Error handling the websocket "
            + request.connectionContext().eventType() + " event of connection " + connectionId, e);
      }
    }

    private synchronized void send(String text) {
      Session current = session;
      if (current == null || !current.isOpen()) {
        return;
      }
      current.sendText(text, Callback.from(() -> { }, error ->
          logger("connection-adapter#sendMessageToConnection").debug("Could not send a websocket message", error)));
    }
  }

  /**
   * Waits until the queued events of every connection have been processed (tests).
   *
   * @param timeout the maximum wait
   * @return whether every queue drained in time
   */
  boolean awaitQuiescence(Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    for (Connection connection : clients.values()) {
      java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
      try {
        connection.executor.execute(latch::countDown);
        if (!latch.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
          return false;
        }
      } catch (java.util.concurrent.RejectedExecutionException e) {
        // closing: nothing more to wait for
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }
}
