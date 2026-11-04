package io.boostercloud.booster.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.*;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;
import graphql.ExecutionResult;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.core.RequestData;
import io.boostercloud.booster.core.UserContext;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local HTTP provider. GraphQL POST requests and POST-based GraphQL-over-SSE streams
 * share the same authentication and authorization. This server does not own Booster.
 */
public final class BoosterHttpServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final Object COMPLETE = new Object();
    private static final Object FAILED = new Object();
    private final Booster booster;
    private final BoosterGraphQL graphql;
    private final Undertow server;
    private final InetSocketAddress address;
    private final Map<String, HttpHandler> routes = new LinkedHashMap<>();
    private final Set<GraphQLWebSocketSession> websocketSessions = ConcurrentHashMap.newKeySet();
    private final Set<GraphQLWebSocketSession> pendingTicks = ConcurrentHashMap.newKeySet();
    private final Semaphore websocketPermits = new Semaphore(64);
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("booster-websocket-expiry").factory());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final Set<HttpExchange> streams = ConcurrentHashMap.newKeySet();
    private final Semaphore streamPermits = new Semaphore(64);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private AuthenticationProvider authentication = AuthenticationProvider.anonymous();

    public BoosterHttpServer(Booster booster, int port) throws IOException {
        this(booster, new InetSocketAddress("127.0.0.1", port));
    }

    public BoosterHttpServer(Booster booster, InetSocketAddress address) throws IOException {
        this.booster = Objects.requireNonNull(booster, "booster");
        this.graphql = new BoosterGraphQL(booster);
        this.address = Objects.requireNonNull(address);
        routes.put("/health", this::health);
        routes.put("/graphql", this::graphql);
        var websocket = new WebSocketProtocolHandshakeHandler(
                List.of(new Hybi13Handshake(Set.of(GraphQLWebSocketSession.SUBPROTOCOL), false)), (io.undertow.websockets.WebSocketConnectionCallback) (exchange, channel) -> {
            ExecutorService messages = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("booster-ws-message", 0).factory());
            var session = new GraphQLWebSocketSession(UUID.randomUUID().toString(), graphql, authentication,
                    data -> {
                        try { WebSockets.sendTextBlocking(data, channel); }
                        catch (IOException failure) { throw new IllegalStateException("WebSocket write failed", failure); }
                    }, () -> {
                        WebSockets.sendClose(1000, "Connection closed", channel, null);
                        messages.shutdown();
                    });
            websocketSessions.add(session);
            channel.addCloseTask(closedChannel -> {
                websocketSessions.remove(session); websocketPermits.release(); messages.shutdownNow();
                try { executor.execute(session::close); } catch (RejectedExecutionException ignored) { }
            });
            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                private final Semaphore pending = new Semaphore(8);
                private final java.util.concurrent.atomic.AtomicInteger pendingBytes = new java.util.concurrent.atomic.AtomicInteger();
                private void terminate() {
                    try { executor.execute(session::close); } catch (RejectedExecutionException ignored) { }
                }
                @Override protected long getMaxTextBufferSize() { return GraphQLWebSocketSession.MAX_MESSAGE_BYTES; }
                @Override protected long getMaxBinaryBufferSize() { return GraphQLWebSocketSession.MAX_MESSAGE_BYTES; }
                @Override protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                    String data = message.getData(); int bytes = data.length() * 2;
                    if (pendingBytes.addAndGet(bytes) > 2_097_152) { pendingBytes.addAndGet(-bytes); terminate(); return; }
                    if (!pending.tryAcquire()) { pendingBytes.addAndGet(-bytes); terminate(); return; }
                    try { messages.execute(() -> { try { session.receive(data); } finally { pending.release(); pendingBytes.addAndGet(-bytes); } }); }
                    catch (RejectedExecutionException failure) { pending.release(); pendingBytes.addAndGet(-bytes); terminate(); }
                }
                @Override protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
                    message.getData().free(); terminate();
                }
                @Override protected void onError(WebSocketChannel channel, Throwable error) { terminate(); }
            });
            channel.resumeReceives();
        });
        this.server = Undertow.builder().addHttpListener(address.getPort(), address.getHostString())
                .setIoThreads(2).setWorkerThreads(8)
                .setServerOption(UndertowOptions.MAX_HEADER_SIZE, 16384)
                .setServerOption(UndertowOptions.MAX_HEADERS, 128)
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, 67_108_864L)
                .setServerOption(UndertowOptions.WEB_SOCKETS_WRITE_TIMEOUT, 5000)
                .setHandler(exchange -> {
                    if (exchange.getRequestPath().equals("/graphql")
                            && "websocket".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Upgrade"))) {
                        String protocols = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Protocol");
                        if (protocols == null || Arrays.stream(protocols.split(",")).map(String::trim)
                                .noneMatch(GraphQLWebSocketSession.SUBPROTOCOL::equals)) {
                            exchange.setStatusCode(400); exchange.endExchange(); return;
                        }
                        if (!websocketPermits.tryAcquire()) { exchange.setStatusCode(503); exchange.endExchange(); return; }
                        try { websocket.handleRequest(exchange); }
                        catch (Exception failure) { websocketPermits.release(); throw failure; }
                        if (!exchange.isUpgrade()) websocketPermits.release();
                        return;
                    }
                    exchange.dispatch(executor, () -> {
                        try (var adapted = new UndertowExchange(exchange)) {
                            String path = exchange.getRequestPath();
                            HttpHandler handler = routes.entrySet().stream()
                                    .filter(entry -> path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/"))
                                    .max(Comparator.comparingInt(entry -> entry.getKey().length())).map(Map.Entry::getValue).orElse(null);
                            if (handler == null) error(adapted, 404, "Not found"); else handler.handle(adapted);
                        } catch (IOException | RuntimeException failure) { exchange.endExchange(); }
                    });
                }).build();
    }

    public BoosterHttpServer authentication(AuthenticationProvider provider) {
        if (started.get()) throw new IllegalStateException("Configure authentication before starting the server");
        authentication = Objects.requireNonNull(provider, "provider");
        return this;
    }

    /** Replace the default health endpoint with a configured provider health tree. */
    public BoosterHttpServer health(HttpHandler handler) {
        if (started.get()) throw new IllegalStateException("Configure health before starting the server");
        routes.put("/health", Objects.requireNonNull(handler)); return this;
    }

    /** Mount a local or rocket handler on the shared HTTP listener before startup. */
    public BoosterHttpServer route(String prefix, HttpHandler handler) {
        if (started.get()) throw new IllegalStateException("Configure routes before starting the server");
        if (prefix == null || !prefix.startsWith("/") || prefix.endsWith("/") || routes.containsKey(prefix))
            throw new IllegalArgumentException("Unique absolute route prefix required");
        routes.put(prefix, Objects.requireNonNull(handler));
        return this;
    }

    public void start() {
        if (closed.get()) throw new IllegalStateException("Server is closed");
        if (started.compareAndSet(false, true)) {
            server.start();
            heartbeat.scheduleAtFixedRate(() -> websocketSessions.forEach(session -> {
                if (!pendingTicks.add(session)) return;
                try { executor.execute(() -> {
                    try { session.tick(); } catch (RuntimeException ignored) { session.close(); }
                    finally { pendingTicks.remove(session); }
                }); } catch (RejectedExecutionException ignored) { pendingTicks.remove(session); }
            }), 1, 1, TimeUnit.SECONDS);
        }
    }

    public int port() { return started.get() ? ((InetSocketAddress) server.getListenerInfo().getFirst().getAddress()).getPort() : address.getPort(); }
    public BoosterGraphQL graphql() { return graphql; }

    private void health(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals("/health")) { error(exchange, 404, "Not found"); return; }
            if (!exchange.getRequestMethod().equals("GET")) { methodNotAllowed(exchange, "GET"); return; }
            respond(exchange, 200, Map.of("status", "UP", "position", booster.position()));
        }
    }

    private void graphql(HttpExchange exchange) throws IOException {
        boolean streaming = exchange.getRequestURI().getPath().equals("/graphql/stream");
        try {
            if (!streaming && !exchange.getRequestURI().getPath().equals("/graphql")) {
                error(exchange, 404, "Not found"); return;
            }
            if (!exchange.getRequestMethod().equals("POST")) { methodNotAllowed(exchange, "POST"); return; }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals("application/json")) {
                error(exchange, 415, "Expected application/json"); return;
            }
            List<String> authorizations = exchange.getRequestHeaders().get("Authorization");
            if (authorizations != null && authorizations.size() != 1) throw new AuthenticationException();
            UserContext user = authentication.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
            String requestId = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (requestId == null) requestId = exchange.getRequestHeaders().getFirst("X-Request-ID");
            if (requestId == null) requestId = UUID.randomUUID().toString();
            if (!requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
                error(exchange, 400, "Invalid request identifier"); return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) { error(exchange, 413, "Request body too large"); return; }
            JsonNode payload = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(body);
            if (payload == null || !payload.isObject() || !payload.path("query").isTextual()
                    || payload.path("query").asText().isBlank()
                    || (payload.hasNonNull("variables") && !payload.get("variables").isObject())
                    || (payload.hasNonNull("operationName") && !payload.get("operationName").isTextual())) {
                error(exchange, 400, "Invalid GraphQL request"); return;
            }
            Map<String, Object> variables = payload.hasNonNull("variables")
                    ? Json.MAPPER.convertValue(payload.get("variables"), Json.MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class))
                    : Map.of();
            RequestContext context = new RequestContext(requestId, user, Map.of("transport", "http"),
                    new RequestData(exchange.getRequestHeaders(), payload, exchange));
            exchange.getResponseHeaders().set("X-Request-ID", requestId);
            ExecutionResult result = graphql.execute(payload.get("query").asText(), variables,
                    payload.hasNonNull("operationName") ? payload.get("operationName").asText() : null, context);
            applyResponseHeaders(exchange, context);
            Object data = result.getData();
            if (data instanceof Publisher<?> publisher) {
                if (!streaming) { error(exchange, 400, "Subscriptions require POST /graphql/stream"); return; }
                stream(exchange, publisher, user);
            } else {
                respond(exchange, 200, result.toSpecification());
            }
        } catch (AuthenticationException exception) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            error(exchange, 401, "Invalid bearer token");
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            error(exchange, 400, "Invalid GraphQL request");
        } catch (RuntimeException exception) {
            error(exchange, 500, "Request failed");
        } finally {
            exchange.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void stream(HttpExchange exchange, Publisher<?> publisher, UserContext authorizedUser) throws IOException {
        if (!streamPermits.tryAcquire()) { error(exchange, 503, "Subscription capacity reached"); return; }
        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(2);
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        streams.add(exchange);
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, 0);
            ((Publisher<ExecutionResult>) publisher).subscribe(new Subscriber<>() {
                @Override public void onSubscribe(Subscription upstream) {
                    if (!subscription.compareAndSet(null, upstream)) { upstream.cancel(); return; }
                    subscriptions.add(upstream);
                    if (closed.get()) upstream.cancel();
                    else upstream.request(1);
                }
                @Override public void onNext(ExecutionResult value) {
                    if (!queue.offer(value)) {
                        Subscription upstream = subscription.get();
                        if (upstream != null) upstream.cancel();
                        queue.clear(); queue.offer(FAILED);
                    }
                }
                @Override public void onError(Throwable error) { queue.offer(FAILED); }
                @Override public void onComplete() { queue.offer(COMPLETE); }
            });
            OutputStream output = exchange.getResponseBody();
            output.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            while (!closed.get()) {
                Object next;
                try { next = queue.poll(15, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
                // Revalidate expiration and any provider policy before every delivery/heartbeat.
                try {
                    UserContext currentUser = authentication.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
                    if (!authorizedUser.equals(currentUser)) throw new AuthenticationException();
                }
                catch (AuthenticationException exception) {
                    output.write("event: error\ndata: {\"errors\":[{\"message\":\"Authentication expired\"}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush(); break;
                }
                if (next == null) {
                    output.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8)); output.flush(); continue;
                }
                if (next == COMPLETE) { output.write("event: complete\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)); output.flush(); break; }
                if (next == FAILED) {
                    output.write("event: error\ndata: {\"errors\":[{\"message\":\"Subscription failed\"}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush(); break;
                }
                output.write(("event: next\ndata: " + Json.MAPPER.writeValueAsString(((ExecutionResult) next).toSpecification()) + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.flush();
                Subscription upstream = subscription.get();
                if (upstream != null) upstream.request(1);
            }
        } finally {
            Subscription upstream = subscription.get();
            if (upstream != null) { upstream.cancel(); subscriptions.remove(upstream); }
            streams.remove(exchange);
            streamPermits.release();
        }
    }

    private static void applyResponseHeaders(HttpExchange exchange, RequestContext context) {
        Set<String> framing = Set.of("connection", "keep-alive", "transfer-encoding", "content-length", "upgrade", "trailer",
                "te", "proxy-authorization", "proxy-authenticate", "content-type", "content-encoding", "x-request-id");
        context.response().snapshot().forEach((name, value) -> {
            if (!framing.contains(name.toLowerCase(Locale.ROOT))) exchange.getResponseHeaders().set(name, value);
        });
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        error(exchange, 405, "Method not allowed");
    }
    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        respond(exchange, status, Map.of("errors", List.of(Map.of("message", message))));
    }
    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            subscriptions.forEach(Subscription::cancel);
            streams.forEach(HttpExchange::close);
            websocketSessions.forEach(GraphQLWebSocketSession::close);
            heartbeat.shutdownNow();
            if (started.get()) server.stop();
            executor.shutdownNow();
        }
    }
}
