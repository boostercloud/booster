package io.boostercloud.booster.transport;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import graphql.ExecutionResult;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.core.RequestData;
import io.boostercloud.booster.core.UserContext;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Legacy graphql-ws protocol, shared by local WebSockets and provider transports such as Web PubSub. */
public final class GraphQLWebSocketSession implements AutoCloseable {
    public static final String SUBPROTOCOL = "graphql-ws";
    public static final int MAX_MESSAGE_BYTES = 1_048_576;
    public record Operation(String id, String query, Map<String, Object> variables, String operationName) {
        public Operation { variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables)); }
    }
    public interface Listener {
        default void initialized(UserContext user, Instant expires) {}
        default void started(Operation operation, RequestContext context) {}
        default void stopped(String operationId) {}
        default void closed() {}
    }
    @FunctionalInterface public interface Executor {
        ExecutionResult execute(Operation operation, RequestContext context);
    }
    private final String connectionId;
    private final Executor executor;
    private final AuthenticationProvider authentication;
    private final Consumer<String> sender;
    private final Runnable closeTransport;
    private final Clock clock;
    private final Duration maxDuration;
    private final Listener listener;
    private final Map<String, Running> operations = new HashMap<>();
    private final Instant initDeadline;
    private Instant expires;
    private UserContext user;
    private String authorization;
    private boolean restored;
    private boolean closed;
    private static final class Running { Subscription subscription; }

    public GraphQLWebSocketSession(String connectionId, BoosterGraphQL graphQL, AuthenticationProvider authentication,
                                  Consumer<String> sender, Runnable closeTransport) {
        this(connectionId, (op, context) -> graphQL.execute(op.query(), op.variables(), op.operationName(), context),
                authentication, sender, closeTransport, Clock.systemUTC(), Duration.ofHours(2), new Listener() {});
    }
    public GraphQLWebSocketSession(String connectionId, Executor executor, AuthenticationProvider authentication,
                                  Consumer<String> sender, Runnable closeTransport, Clock clock, Duration maxDuration, Listener listener) {
        if (connectionId == null || connectionId.isBlank()) throw new IllegalArgumentException("Connection ID required");
        if (maxDuration.isNegative() || maxDuration.isZero()) throw new IllegalArgumentException("Positive connection duration required");
        this.connectionId = connectionId; this.executor = Objects.requireNonNull(executor);
        this.authentication = Objects.requireNonNull(authentication); this.sender = Objects.requireNonNull(sender);
        this.closeTransport = Objects.requireNonNull(closeTransport); this.clock = Objects.requireNonNull(clock);
        this.maxDuration = maxDuration; this.listener = Objects.requireNonNull(listener);
        this.initDeadline = clock.instant().plusSeconds(10);
    }

    /** Restore only trusted, provider-persisted identity; expiry is checked again on every message and delivery. */
    public synchronized void restore(UserContext trustedIdentity, Instant connectionExpiry) {
        if (user != null || closed) throw new IllegalStateException("Connection already initialized or closed");
        user = Objects.requireNonNull(trustedIdentity); expires = Objects.requireNonNull(connectionExpiry); restored = true;
        if (!clock.instant().isBefore(expires) || tokenExpired(user)) { close(); throw new AuthenticationException(); }
    }

    public synchronized void receive(String raw) {
        if (closed) return;
        try {
            if (raw == null || raw.length() > MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Invalid message");
            JsonNode message = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(raw);
            if (message == null || !message.isObject() || !message.path("type").isTextual()) throw new IllegalArgumentException("Invalid message");
            switch (message.path("type").asText()) {
                case "connection_init" -> initialize(message.get("payload"));
                case "start" -> { requireAuthenticated(); start(message); }
                case "stop" -> { requireAuthenticated(); stop(id(message), true); }
                case "connection_terminate" -> close();
                default -> connectionError("Unknown message type");
            }
        } catch (AuthenticationException error) { connectionError("Authentication expired or invalid"); close(); }
        catch (Exception error) { connectionError("Invalid GraphQL WebSocket message"); }
    }
    private void initialize(JsonNode payload) {
        if (user != null) { connectionError("Connection already initialized"); close(); return; }
        if (!clock.instant().isBefore(initDeadline)) throw new AuthenticationException();
        if (payload != null && !payload.isNull() && !payload.isObject()) throw new IllegalArgumentException();
        JsonNode header = payload == null ? null : payload.get("Authorization");
        if (header == null && payload != null) header = payload.get("authorization");
        if (header != null && !header.isTextual()) throw new AuthenticationException();
        authorization = header == null ? null : header.asText();
        user = authentication.authenticate(authorization);
        expires = clock.instant().plus(maxDuration);
        if (tokenExpired(user)) throw new AuthenticationException();
        Object expiration = user.claims().get("exp");
        if (expiration instanceof Number value) {
            var seconds = new java.math.BigDecimal(value.toString());
            if (seconds.compareTo(java.math.BigDecimal.valueOf(expires.getEpochSecond())) < 0)
                expires = Instant.ofEpochSecond(seconds.setScale(0, java.math.RoundingMode.CEILING).longValueExact());
        }
        try { listener.initialized(user, expires); }
        catch (RuntimeException failure) { connectionError("Connection persistence failed"); close(); return; }
        send(Map.of("type", "connection_ack"));
    }
    @SuppressWarnings("unchecked") private void start(JsonNode message) {
        String id = id(message);
        if (operations.containsKey(id)) { operationError(id, "Operation ID already active"); return; }
        if (operations.size() >= 64) { operationError(id, "Operation capacity reached"); return; }
        JsonNode payload = message.path("payload");
        if (!payload.isObject() || !payload.path("query").isTextual() || payload.path("query").asText().isBlank()
                || (payload.hasNonNull("variables") && !payload.path("variables").isObject())
                || (payload.hasNonNull("operationName") && !payload.path("operationName").isTextual())) {
            operationError(id, "Message payload must contain a query and valid variables"); return;
        }
        Map<String, Object> variables = payload.hasNonNull("variables") ? Json.MAPPER.convertValue(payload.path("variables"), Map.class) : Map.of();
        Operation op = new Operation(id, payload.path("query").asText(), variables,
                payload.hasNonNull("operationName") ? payload.path("operationName").asText() : null);
        var context = new RequestContext("ws:" + connectionId + ":" + UUID.randomUUID(), user,
                Map.of("transport", "websocket", "connectionId", connectionId, "operationId", id),
                new RequestData(authorization == null ? Map.of() : Map.of("Authorization", List.of(authorization)), payload, this));
        ExecutionResult result;
        try { result = executor.execute(op, context); }
        catch (RuntimeException failure) { operationError(id, "Operation failed"); return; }
        if (result.getData() instanceof Publisher<?> publisher) {
            Running running = new Running(); operations.put(id, running);
            try { listener.started(op, context); }
            catch (RuntimeException failure) { operations.remove(id); operationError(id, "Operation registration failed"); return; }
            ((Publisher<ExecutionResult>) publisher).subscribe(new Subscriber<>() {
                @Override public void onSubscribe(Subscription subscription) {
                    synchronized (GraphQLWebSocketSession.this) {
                        if (closed || operations.get(id) != running) { subscription.cancel(); return; }
                        running.subscription = subscription; subscription.request(1);
                    }
                }
                @Override public void onNext(ExecutionResult event) {
                    synchronized (GraphQLWebSocketSession.this) {
                        if (closed || operations.get(id) != running) return;
                        try { requireAuthenticated(); send(Map.of("type", "data", "id", id, "payload", event.toSpecification())); }
                        catch (RuntimeException failure) { connectionError("Authentication expired or delivery failed"); close(); return; }
                        running.subscription.request(1);
                    }
                }
                @Override public void onError(Throwable failure) {
                    synchronized (GraphQLWebSocketSession.this) { if (operations.get(id) == running) { operationError(id, "Subscription failed"); stop(id, true); } }
                }
                @Override public void onComplete() {
                    synchronized (GraphQLWebSocketSession.this) { if (operations.get(id) == running) stop(id, true); }
                }
            });
        } else {
            send(Map.of("type", "data", "id", id, "payload", result.toSpecification()));
            send(Map.of("type", "complete", "id", id));
        }
    }
    private void stop(String id, boolean complete) {
        Running running = operations.remove(id);
        if (running != null && running.subscription != null) running.subscription.cancel();
        listener.stopped(id);
        if (complete) send(Map.of("type", "complete", "id", id));
    }
    public synchronized void tick() {
        if (closed) return;
        try {
            if (user == null) { if (!clock.instant().isBefore(initDeadline)) throw new AuthenticationException(); }
            else requireAuthenticated();
        } catch (AuthenticationException failure) { connectionError("Connection expired"); close(); }
    }
    private void requireAuthenticated() {
        if (user == null || !clock.instant().isBefore(expires) || tokenExpired(user)) throw new AuthenticationException();
        if (!restored && !user.equals(authentication.authenticate(authorization))) throw new AuthenticationException();
    }
    private boolean tokenExpired(UserContext identity) {
        Object exp = identity.claims().get("exp");
        return exp instanceof Number number && java.math.BigDecimal.valueOf(clock.instant().getEpochSecond())
                .compareTo(new java.math.BigDecimal(number.toString())) >= 0;
    }
    private static String id(JsonNode message) {
        if (!message.path("id").isTextual() || message.path("id").asText().isEmpty() || message.path("id").asText().length() > 128)
            throw new IllegalArgumentException("Operation ID required");
        return message.path("id").asText();
    }
    private void operationError(String id, String message) { send(Map.of("type", "error", "id", id, "payload", Map.of("errors", List.of(Map.of("message", message))))); }
    private void connectionError(String message) { if (!closed) send(Map.of("type", "connection_error", "payload", message)); }
    private void send(Object message) {
        try { sender.accept(Json.MAPPER.writeValueAsString(message)); }
        catch (Exception error) { close(); throw new IllegalStateException("WebSocket delivery failed"); }
    }
    public synchronized int activeOperations() { return operations.size(); }
    public synchronized boolean isClosed() { return closed; }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (String id : List.copyOf(operations.keySet())) {
            try { stop(id, false); } catch (RuntimeException ignored) { /* Continue cancellation and transport cleanup. */ }
        }
        try { listener.closed(); } finally { closeTransport.run(); }
    }
}
