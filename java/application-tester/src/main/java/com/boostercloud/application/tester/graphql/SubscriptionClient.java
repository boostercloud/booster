package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.graphql.GraphQLWebsocketMessages;
import com.boostercloud.framework.types.graphql.MessageTypes;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A GraphQL subscriptions client speaking the legacy {@code subscriptions-transport-ws} protocol (websocket
 * sub-protocol {@value GraphQLWebsocketMessages#SUBPROTOCOL}) over {@link java.net.http.WebSocket}: a port of the
 * {@code SubscriptionClient} of {@code subscriptions-transport-ws} 0.11.0, the client Booster's TypeScript
 * {@code GraphQLHelper} uses. The state machine, messages, timers and reconnection rules are the library's:
 *
 * <ul>
 *   <li>On open: emit {@code connecting} (or {@code reconnecting}), send
 *       {@code {"type":"connection_init","payload":<connectionParams>}}, then flush the messages queued while
 *       connecting. On {@code connection_ack}: emit {@code connected} (or {@code reconnected}), reset the backoffs and
 *       call the connection callback with {@code null}; on {@code connection_error}: call it with the payload.
 *   <li>Each operation gets the next id ({@code "1"}, {@code "2"}, ...) and is sent as
 *       {@code {"id","type":"start","payload":{"variables","extensions","operationName","query"}}}; {@code data},
 *       {@code error} and {@code complete} messages are routed to its observer ({@code error} and {@code complete}
 *       end it); messages for unknown ids are ignored. Unsubscribing sends {@code {"id","type":"stop"}}.
 *   <li>{@link #close()} (forced) sends {@code stop} for every operation and
 *       {@code {"type":"connection_terminate","payload":null}}, closes the socket and never reconnects.
 *       {@link #close(boolean) close(false)} only closes the socket: with {@code reconnect} the client reconnects
 *       after a backoff (100 ms doubling, 50% jitter, at most 10 s), re-sends {@code connection_init} and a
 *       {@code start} for every active operation with the same ids, and emits {@code reconnected} on the ack. A socket
 *       closed by the server (or failing) reconnects the same way.
 *   <li>A connection that is not open after the "max connect time" (1 s, growing by 1.2 per attempt, at most
 *       {@code timeout}) is closed and retried. Keep-alive ({@code ka}) messages arm a check every {@code timeout}
 *       ms that reconnects when no {@code ka} arrived in between.
 * </ul>
 *
 * <p>Threading: the client runs on its own event loop (a virtual thread that exists only while there is work), so
 * the state machine is single-threaded like in JavaScript. Public methods may be called from any thread; they run
 * on the loop and wait for it. {@link OperationObserver}s and event listeners are called on the loop and must not
 * block waiting for this client (see {@link #isCallbackThread()}).
 *
 * <p>Differences from the library: no middlewares ({@code use}/{@code applyMiddlewares}); the socket is closed with
 * status 1000 (the JavaScript client sends no status); a server that does not select the {@code graphql-ws}
 * sub-protocol fails the connection like the {@code ws} package ({@code Server sent no subprotocol}); a forced
 * {@link #close()} also cancels a pending reconnection when no socket is open (the library ignores it and
 * reconnects later); unparseable or unknown messages are reported to the {@code error} listeners (and stderr)
 * instead of throwing an uncaught exception.
 */
public final class SubscriptionClient {

  /** The {@code readyState} of the current socket. */
  public enum Status {
    /** The handshake is in progress. */
    CONNECTING,
    /** The socket is open. */
    OPEN,
    /** The socket is closing. */
    CLOSING,
    /** There is no open socket. */
    CLOSED
  }

  /** Something that can be unsubscribed. */
  public interface Unsubscribable {
    /** Stops receiving (sends {@code stop} if the operation is active); idempotent. */
    void unsubscribe();
  }

  /** The result of {@link #request(Operation)}: starts the operation once per {@code subscribe}. */
  public interface OperationRequest {
    /**
     * Starts the operation.
     *
     * @param observer receives its messages
     * @return the handle that stops it
     */
    Unsubscribable subscribe(OperationObserver observer);
  }

  /** Keep-alive message type ({@code GQL_CONNECTION_KEEP_ALIVE}), not in Booster's {@link MessageTypes}. */
  public static final String GQL_CONNECTION_KEEP_ALIVE = "ka";

  static final String EVENT_CONNECTED = "connected";
  static final String EVENT_CONNECTING = "connecting";
  static final String EVENT_DISCONNECTED = "disconnected";
  static final String EVENT_RECONNECTED = "reconnected";
  static final String EVENT_RECONNECTING = "reconnecting";
  static final String EVENT_ERROR = "error";

  private static final Object UNDEFINED = new Object();
  private static final long CLOSE_TIMEOUT_MS = 30000;
  private static final int ABNORMAL_CLOSURE = 1006;
  private static final AtomicInteger CLIENT_IDS = new AtomicInteger();
  private static final Set<String> OPERATION_MESSAGES =
      Set.of(MessageTypes.GQL_DATA.value(), MessageTypes.GQL_COMPLETE.value(), MessageTypes.GQL_ERROR.value());

  private final HttpClient httpClient;
  private final String url;
  private final SubscriptionClientOptions options;
  private final EventLoop loop;
  private final Backoff backoff;
  private final Backoff maxConnectTimeGenerator;
  private final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

  // Loop-confined state (the fields of the JavaScript class)
  private final Map<String, OperationEntry> operations = new LinkedHashMap<>();
  private final List<Map<String, Object>> unsentMessagesQueue = new ArrayList<>();
  private int nextOperationId;
  private boolean reconnecting;
  private boolean closedByUser;
  private Connection client;
  private Boolean wasKeepAliveReceived;
  private EventLoop.Timer checkConnectionInterval;
  private EventLoop.Timer maxConnectTimeout;
  private EventLoop.Timer tryReconnectTimeout;
  private EventLoop.Timer inactivityTimeout;

  private record OperationEntry(Operation options, OperationObserver handler) {}

  /**
   * Creates the client with its own {@link HttpClient}; unless {@code lazy}, it starts connecting at once.
   *
   * @param url the websocket URL ({@code ws://} or {@code wss://})
   * @param options the options
   */
  public SubscriptionClient(String url, SubscriptionClientOptions options) {
    this(HttpClient.newHttpClient(), url, options);
  }

  /**
   * Creates the client; unless {@code lazy}, it starts connecting at once.
   *
   * @param httpClient the client that opens the websockets
   * @param url the websocket URL ({@code ws://} or {@code wss://})
   * @param options the options
   */
  public SubscriptionClient(HttpClient httpClient, String url, SubscriptionClientOptions options) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.url = Objects.requireNonNull(url, "url");
    this.options = options == null ? SubscriptionClientOptions.defaults() : options;
    this.loop = new EventLoop("graphql-ws-client-" + CLIENT_IDS.incrementAndGet());
    this.backoff = new Backoff(100, 10000, 2, 0.5, this.options.random());
    this.maxConnectTimeGenerator =
        new Backoff(this.options.minTimeout(), this.options.timeout(), 1.2, 0, this.options.random());
    if (!this.options.lazy()) {
      loop.run(this::connect);
    }
  }

  /**
   * @return the websocket URL
   */
  public String url() {
    return url;
  }

  /**
   * @return the {@code readyState} of the current socket ({@link Status#CLOSED} when there is none)
   */
  public Status status() {
    return loop.call(this::currentStatus);
  }

  /**
   * @return the ids of the active operations, in creation order (Java only, for diagnostics)
   */
  public List<String> activeOperationIds() {
    return loop.call(() -> List.copyOf(operations.keySet()));
  }

  /**
   * @return whether the calling thread is this client's event loop (observers and listeners run there; blocking on
   *     the client from them would deadlock)
   */
  public boolean isCallbackThread() {
    return loop.inLoop();
  }

  /** {@code close()}: forced close (stop all operations, terminate, never reconnect). */
  public void close() {
    close(true, true);
  }

  /**
   * {@code close(isForced)}.
   *
   * @param isForced {@code true} to stop all operations and terminate; {@code false} to close the socket only (the
   *     client reconnects when {@code reconnect} is set)
   */
  public void close(boolean isForced) {
    close(isForced, true);
  }

  /**
   * {@code close(isForced, closedByUser)}.
   *
   * @param isForced see {@link #close(boolean)}
   * @param closedByUser whether a later server close of the (new) socket must be ignored until the next open
   */
  public void close(boolean isForced, boolean closedByUser) {
    loop.run(() -> doClose(isForced, closedByUser));
  }

  /**
   * {@code request(operation)}: an observable-like request; each {@code subscribe} starts the operation with a new
   * id.
   *
   * @param operation the operation
   * @return the request
   */
  public OperationRequest request(Operation operation) {
    Objects.requireNonNull(operation, "operation");
    loop.run(this::clearInactivityTimeout);
    return observer -> {
      Objects.requireNonNull(observer, "observer");
      String opId = loop.call(() -> executeOperation(operation, observer));
      return new Unsubscribable() {
        private String id = opId;

        @Override
        public void unsubscribe() {
          loop.run(() -> {
            if (id != null) {
              SubscriptionClient.this.unsubscribe(id);
              id = null;
            }
          });
        }
      };
    };
  }

  /**
   * {@code unsubscribe(opId)}: forgets the operation and sends {@code stop} (when it is active).
   *
   * @param opId the operation id
   */
  public void unsubscribe(String opId) {
    loop.run(() -> doUnsubscribe(opId));
  }

  /** {@code unsubscribeAll()}: unsubscribes every active operation. */
  public void unsubscribeAll() {
    loop.run(this::doUnsubscribeAll);
  }

  /**
   * @param callback called with the {@code connection_ack} payload on the first acknowledgement of a connection
   * @return a function that removes the listener
   */
  public Runnable onConnected(Consumer<Object> callback) {
    return on(EVENT_CONNECTED, callback);
  }

  /**
   * @param callback called when a socket opens (not after a reconnection)
   * @return a function that removes the listener
   */
  public Runnable onConnecting(Runnable callback) {
    return on(EVENT_CONNECTING, ignored -> callback.run());
  }

  /**
   * @param callback called whenever the socket is dropped by the client
   * @return a function that removes the listener
   */
  public Runnable onDisconnected(Runnable callback) {
    return on(EVENT_DISCONNECTED, ignored -> callback.run());
  }

  /**
   * @param callback called with the {@code connection_ack} payload when a reconnection is acknowledged
   * @return a function that removes the listener
   */
  public Runnable onReconnected(Consumer<Object> callback) {
    return on(EVENT_RECONNECTED, callback);
  }

  /**
   * @param callback called when a socket opens while reconnecting
   * @return a function that removes the listener
   */
  public Runnable onReconnecting(Runnable callback) {
    return on(EVENT_RECONNECTING, ignored -> callback.run());
  }

  /**
   * @param callback called with socket errors and messages that could not be sent
   * @return a function that removes the listener
   */
  public Runnable onError(Consumer<Throwable> callback) {
    return on(EVENT_ERROR, error -> callback.accept((Throwable) error));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Package API used by GraphQLClient (always called on the loop)

  EventLoop loop() {
    return loop;
  }

  /** Starts an operation from the loop and returns the function that stops it. */
  Runnable startOnLoop(Operation operation, OperationObserver observer) {
    String opId = executeOperation(operation, observer);
    return () -> doUnsubscribe(opId);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // The state machine (loop-confined)

  private Runnable on(String event, Consumer<Object> callback) {
    Objects.requireNonNull(callback, "callback");
    List<Consumer<Object>> eventListeners = listeners.computeIfAbsent(event, ignored -> new CopyOnWriteArrayList<>());
    eventListeners.add(callback);
    return () -> eventListeners.remove(callback);
  }

  private void emit(String event, Object argument) {
    for (Consumer<Object> listener : listeners.getOrDefault(event, List.of())) {
      try {
        listener.accept(argument);
      } catch (RuntimeException error) {
        EventLoop.reportUncaught(error);
      }
    }
  }

  private Status currentStatus() {
    return client == null ? Status.CLOSED : client.state;
  }

  private void doClose(boolean isForced, boolean closedByUserFlag) {
    clearInactivityTimeout();
    if (client != null) {
      this.closedByUser = closedByUserFlag;
      if (isForced) {
        clearCheckConnectionInterval();
        clearMaxConnectTimeout();
        clearTryReconnectTimeout();
        doUnsubscribeAll();
        sendMessage(null, MessageTypes.GQL_CONNECTION_TERMINATE.value(), null);
      }
      Connection closing = client;
      closing.close();
      client = null;
      emit(EVENT_DISCONNECTED, null);
      if (!isForced) {
        tryReconnect();
      }
    } else if (isForced) {
      // Java only: without a socket the library ignores close() and a pending reconnection would resurrect the
      // client; a forced close always ends it.
      clearCheckConnectionInterval();
      clearMaxConnectTimeout();
      clearTryReconnectTimeout();
      operations.clear();
      unsentMessagesQueue.clear();
      reconnecting = false;
    }
  }

  private String executeOperation(Operation operation, OperationObserver handler) {
    if (client == null) {
      connect();
    }
    String opId = String.valueOf(++nextOperationId);
    operations.put(opId, new OperationEntry(operation, handler));
    if (operation.query().isEmpty()) {
      doUnsubscribe(opId);
      handler.error(new IllegalArgumentException("Must provide a query."));
    } else if (operations.containsKey(opId)) {
      sendMessage(opId, MessageTypes.GQL_START.value(), operation.toStartPayload());
    }
    return opId;
  }

  private void doUnsubscribe(String opId) {
    if (opId != null && operations.remove(opId) != null) {
      setInactivityTimeout();
      sendMessage(opId, MessageTypes.GQL_STOP.value(), UNDEFINED);
    }
  }

  private void doUnsubscribeAll() {
    for (String opId : new ArrayList<>(operations.keySet())) {
      doUnsubscribe(opId);
    }
  }

  private Map<String, Object> buildMessage(String id, String type, Object payload) {
    Map<String, Object> message = new LinkedHashMap<>();
    if (id != null) {
      message.put("id", id);
    }
    message.put("type", type);
    if (payload != UNDEFINED) {
      message.put("payload", payload);
    }
    return message;
  }

  private void sendMessage(String id, String type, Object payload) {
    sendMessageRaw(buildMessage(id, type, payload));
  }

  private void sendMessageRaw(Map<String, Object> message) {
    switch (currentStatus()) {
      case OPEN -> client.send(BoosterJson.toJson(message));
      case CONNECTING -> unsentMessagesQueue.add(message);
      default -> {
        if (!reconnecting) {
          emit(EVENT_ERROR, new IllegalStateException(
              "A message was not sent because socket is not connected, is closing or is already closed. Message was: "
                  + BoosterJson.toJson(message)));
        }
      }
    }
  }

  private void tryReconnect() {
    if (!options.reconnect() || backoff.attempts() >= options.reconnectionAttempts()) {
      return;
    }
    if (!reconnecting) {
      operations.forEach((opId, entry) ->
          unsentMessagesQueue.add(buildMessage(opId, MessageTypes.GQL_START.value(), entry.options().toStartPayload())));
      reconnecting = true;
    }
    clearTryReconnectTimeout();
    long delay = backoff.duration();
    tryReconnectTimeout = loop.schedule(this::connect, delay);
  }

  private void flushUnsentMessagesQueue() {
    List<Map<String, Object>> messages = new ArrayList<>(unsentMessagesQueue);
    unsentMessagesQueue.clear();
    messages.forEach(this::sendMessageRaw);
  }

  private void checkConnection() {
    if (Boolean.TRUE.equals(wasKeepAliveReceived)) {
      wasKeepAliveReceived = false;
      return;
    }
    if (!reconnecting) {
      doClose(false, true);
    }
  }

  private void checkMaxConnectTimeout() {
    clearMaxConnectTimeout();
    maxConnectTimeout = loop.schedule(() -> {
      if (currentStatus() != Status.OPEN) {
        reconnecting = true;
        doClose(false, true);
      }
    }, maxConnectTimeGenerator.duration());
  }

  private void connect() {
    client = new Connection();
    checkMaxConnectTimeout();
  }

  private void clientOnOpen(Connection connection) {
    if (connection != client || currentStatus() != Status.OPEN) {
      return;
    }
    clearMaxConnectTimeout();
    closedByUser = false;
    emit(reconnecting ? EVENT_RECONNECTING : EVENT_CONNECTING, null);
    Object connectionParams;
    try {
      connectionParams = options.connectionParams().get();
    } catch (RuntimeException error) {
      // JSON.stringify(new Error(...)) is {}
      sendMessage(null, MessageTypes.GQL_CONNECTION_ERROR.value(), new LinkedHashMap<>());
      flushUnsentMessagesQueue();
      return;
    }
    sendMessage(null, MessageTypes.GQL_CONNECTION_INIT.value(), connectionParams == null ? UNDEFINED : connectionParams);
    flushUnsentMessagesQueue();
  }

  private void clientOnClose(Connection connection) {
    if (connection != client) {
      return;
    }
    if (!closedByUser) {
      doClose(false, false);
    }
  }

  @SuppressWarnings("unchecked")
  private void processReceivedData(String receivedData) {
    Object parsed;
    try {
      parsed = BoosterJson.parse(receivedData);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Message must be JSON-parseable. Got: " + receivedData, e);
    }
    Map<String, Object> parsedMessage = parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    Object rawId = parsedMessage.get("id");
    String opId = rawId == null ? null : Js.string(rawId);
    Object type = parsedMessage.get("type");
    Object payload = parsedMessage.get("payload");

    if (type instanceof String messageType && OPERATION_MESSAGES.contains(messageType)
        && (opId == null || !operations.containsKey(opId))) {
      doUnsubscribe(opId);
      return;
    }

    String messageType = type instanceof String text ? text : "";
    if (messageType.equals(MessageTypes.GQL_CONNECTION_ERROR.value())) {
      if (options.connectionCallback() != null) {
        options.connectionCallback().accept(payload);
      }
    } else if (messageType.equals(MessageTypes.GQL_CONNECTION_ACK.value())) {
      emit(reconnecting ? EVENT_RECONNECTED : EVENT_CONNECTED, payload);
      reconnecting = false;
      backoff.reset();
      maxConnectTimeGenerator.reset();
      if (options.connectionCallback() != null) {
        options.connectionCallback().accept(null);
      }
    } else if (messageType.equals(MessageTypes.GQL_COMPLETE.value())) {
      OperationEntry entry = operations.remove(opId);
      entry.handler().complete();
    } else if (messageType.equals(MessageTypes.GQL_ERROR.value())) {
      operations.get(opId).handler().error(GraphQLClientException.ofOperationErrors(formatErrors(payload)));
      operations.remove(opId);
    } else if (messageType.equals(MessageTypes.GQL_DATA.value())) {
      Map<String, Object> result =
          payload instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
      if (Js.truthy(result.get("errors"))) {
        result.put("errors", formatErrors(result.get("errors")).stream().map(GraphQLError::toMap).toList());
      }
      operations.get(opId).handler().next(result);
    } else if (messageType.equals(GQL_CONNECTION_KEEP_ALIVE)) {
      boolean firstKA = wasKeepAliveReceived == null;
      wasKeepAliveReceived = true;
      if (firstKA) {
        checkConnection();
      }
      if (checkConnectionInterval != null) {
        checkConnectionInterval.cancel();
        checkConnection();
      }
      checkConnectionInterval = loop.scheduleAtFixedRate(this::checkConnection, options.timeout());
    } else {
      throw new IllegalStateException("Invalid message type!");
    }
  }

  /** {@code formatErrors}: ensures an array of errors. */
  static List<GraphQLError> formatErrors(Object errors) {
    if (errors instanceof List<?> list) {
      return list.stream().map(GraphQLError::of).toList();
    }
    if (errors instanceof Map<?, ?> map && Js.truthy(map.get("errors"))) {
      return formatErrors(map.get("errors"));
    }
    if (errors instanceof Map<?, ?> map && Js.truthy(map.get("message"))) {
      return List.of(GraphQLError.of(map));
    }
    Map<String, Object> unknown = new LinkedHashMap<>();
    unknown.put("name", "FormatedError");
    unknown.put("message", "Unknown error");
    unknown.put("originalError", errors);
    return List.of(GraphQLError.of(unknown));
  }

  private void setInactivityTimeout() {
    if (options.inactivityTimeout() > 0 && operations.isEmpty()) {
      inactivityTimeout = loop.schedule(() -> {
        if (operations.isEmpty()) {
          doClose(true, true);
        }
      }, options.inactivityTimeout());
    }
  }

  private void clearInactivityTimeout() {
    if (inactivityTimeout != null) {
      inactivityTimeout.cancel();
      inactivityTimeout = null;
    }
  }

  private void clearCheckConnectionInterval() {
    if (checkConnectionInterval != null) {
      checkConnectionInterval.cancel();
      checkConnectionInterval = null;
    }
  }

  private void clearMaxConnectTimeout() {
    if (maxConnectTimeout != null) {
      maxConnectTimeout.cancel();
      maxConnectTimeout = null;
    }
  }

  private void clearTryReconnectTimeout() {
    if (tryReconnectTimeout != null) {
      tryReconnectTimeout.cancel();
      tryReconnectTimeout = null;
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // One websocket (the `ws` instance of the JavaScript client)

  private final class Connection implements WebSocket.Listener {
    private final CompletableFuture<WebSocket> handshake;
    private final StringBuilder text = new StringBuilder();
    private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
    // loop-confined
    private Status state = Status.CONNECTING;
    private WebSocket webSocket;
    private boolean detached;
    private boolean terminated;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    Connection() {
      CompletableFuture<WebSocket> started;
      try {
        started = httpClient.newWebSocketBuilder()
            .subprotocols(GraphQLWebsocketMessages.SUBPROTOCOL)
            .buildAsync(URI.create(url), this);
      } catch (RuntimeException e) {
        started = CompletableFuture.failedFuture(e);
      }
      handshake = started;
      handshake.whenComplete((ws, error) -> {
        if (error != null) {
          Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
          loop.execute(() -> handleError(cause));
        }
      });
    }

    /** {@code ws.send(text)}: queued after the previous sends. */
    void send(String message) {
      WebSocket ws = webSocket;
      sendChain = sendChain
          .handle((ignored, error) -> null)
          .thenCompose(ignored -> ws.sendText(message, true))
          .exceptionally(error -> {
            loop.execute(() -> handleError(error));
            return null;
          });
    }

    /** {@code ws.close()}: detaches the client handlers, then closes gracefully (or aborts the handshake). */
    void close() {
      detached = true;
      if (state == Status.CONNECTING) {
        state = Status.CLOSED;
        handshake.thenAccept(WebSocket::abort);
        return;
      }
      if (state == Status.OPEN) {
        state = Status.CLOSING;
        WebSocket ws = webSocket;
        sendChain = sendChain
            .handle((ignored, error) -> null)
            .thenCompose(ignored -> ws.sendClose(WebSocket.NORMAL_CLOSURE, ""))
            .whenComplete((ignored, error) -> {
              if (error != null) {
                ws.abort();
              } else {
                loop.schedule(() -> {
                  if (!ws.isInputClosed()) {
                    ws.abort();
                  }
                }, CLOSE_TIMEOUT_MS);
              }
            });
      }
    }

    private void handleOpen(WebSocket ws) {
      webSocket = ws;
      if (detached) {
        ws.abort();
        return;
      }
      String subprotocol = ws.getSubprotocol();
      if (!GraphQLWebsocketMessages.SUBPROTOCOL.equals(subprotocol)) {
        ws.abort();
        handleError(new IllegalStateException(
            subprotocol == null || subprotocol.isEmpty()
                ? "Server sent no subprotocol"
                : "Server sent an invalid subprotocol"));
        return;
      }
      state = Status.OPEN;
      options.socketLogger().debug("[GraphQL socket] on open");
      clientOnOpen(this);
    }

    private void handleMessage(String data) {
      options.socketLogger().debug("[GraphQL socket] on message: ", data);
      if (detached || this != client) {
        return;
      }
      try {
        processReceivedData(data);
      } catch (IllegalStateException error) {
        emit(EVENT_ERROR, error);
        EventLoop.reportUncaught(error);
      }
    }

    private void handleClose(int statusCode, String reason) {
      if (terminated) {
        return;
      }
      terminated = true;
      state = Status.CLOSED;
      options.socketLogger().debug("[GraphQL socket] on close: ", statusCode, reason);
      if (!detached) {
        clientOnClose(this);
      }
    }

    private void handleError(Throwable error) {
      if (terminated) {
        return;
      }
      options.socketLogger().debug("[GraphQL socket] on error: ", FetchError.reason(error));
      if (!detached && this == client) {
        emit(EVENT_ERROR, error);
      }
      if (webSocket != null) {
        webSocket.abort();
      }
      handleClose(ABNORMAL_CLOSURE, "");
    }

    // WebSocket.Listener: called on the HttpClient's threads, forwarded to the loop

    @Override
    public void onOpen(WebSocket ws) {
      loop.execute(() -> handleOpen(ws));
      ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      text.append(data);
      if (last) {
        String message = text.toString();
        text.setLength(0);
        loop.execute(() -> handleMessage(message));
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
      byte[] bytes = new byte[data.remaining()];
      data.get(bytes);
      binary.writeBytes(bytes);
      if (last) {
        String message = binary.toString(StandardCharsets.UTF_8);
        binary.reset();
        loop.execute(() -> handleMessage(message));
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
      loop.execute(() -> options.socketLogger().debug("[GraphQL socket] on \"ping\""));
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) {
      loop.execute(() -> options.socketLogger().debug("[GraphQL socket] on \"pong\""));
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      loop.execute(() -> handleClose(statusCode, reason));
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
      loop.execute(() -> handleError(error));
    }
  }
}
