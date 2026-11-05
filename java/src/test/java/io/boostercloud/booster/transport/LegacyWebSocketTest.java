package io.boostercloud.booster.transport;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.store.JdbcEventStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LegacyWebSocketTest {
    @Test void existingLegacyClientInitializesQueriesSubscribesStopsAndTerminatesOnTheHttpPort() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var listener = new Messages();
            WebSocket socket = client.newWebSocketBuilder().subprotocols("graphql-ws")
                    .connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/graphql"), listener).join();
            assertEquals("graphql-ws", socket.getSubprotocol());
            send(socket, Map.of("type", "connection_init", "payload", Map.of()));
            assertEquals("connection_ack", listener.next().path("type").asText());
            send(socket, Map.of("id", "q", "type", "start", "payload", Map.of("query", "{health}")));
            assertTrue(listener.next().at("/payload/data/health").asBoolean());
            assertEquals("complete", listener.next().path("type").asText());
            send(socket, Map.of("id", "s", "type", "start", "payload", Map.of("query", "subscription { CartView(id:\"ws-cart\") {id totalQuantity} }")));
            // A query following start is an ordering barrier on the connection.
            send(socket, Map.of("id", "barrier", "type", "start", "payload", Map.of("query", "{health}")));
            assertEquals("barrier", listener.next().path("id").asText()); listener.next();
            app.executeCommand("OpenCart", Json.MAPPER.valueToTree(Map.of("cartId", "ws-cart")), RequestContext.anonymous());
            JsonNode event = listener.next();
            assertEquals("data", event.path("type").asText());
            assertEquals("ws-cart", event.at("/payload/data/CartView/id").asText());
            send(socket, Map.of("id", "s", "type", "stop"));
            assertEquals("complete", listener.next().path("type").asText());
            send(socket, Map.of("type", "connection_terminate"));
            assertEquals(1000, listener.closed.get(5, TimeUnit.SECONDS));
        }
    }

    @Test void rejectsUnauthenticatedStartWrongSubprotocolAndInvalidPayload() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0); var client = HttpClient.newHttpClient()) {
            server.start(); URI address = URI.create("ws://127.0.0.1:" + server.port() + "/graphql");
            assertThrows(CompletionException.class, () -> client.newWebSocketBuilder().subprotocols("graphql-transport-ws")
                    .buildAsync(address, new Messages()).join());
            var listener = new Messages();
            WebSocket socket = client.newWebSocketBuilder().subprotocols("graphql-ws").buildAsync(address, listener).join();
            send(socket, Map.of("id", "s", "type", "start", "payload", Map.of("query", "{health}")));
            assertEquals("connection_error", listener.next().path("type").asText());
            assertEquals(1000, listener.closed.get(5, TimeUnit.SECONDS));
            var errors = new Messages();
            WebSocket valid = client.newWebSocketBuilder().subprotocols("graphql-ws").buildAsync(address, errors).join();
            send(valid, Map.of("type", "connection_init")); errors.next();
            send(valid, Map.of("id", "bad", "type", "start", "payload", Map.of("variables", List.of())));
            assertEquals("error", errors.next().path("type").asText());
            valid.sendClose(1000, "done").join();
        }
    }

    @Test void revalidatesRoleChangesAndExpiresRestoredProviderConnections() {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
            List<String> messages = new ArrayList<>(); AtomicBoolean closed = new AtomicBoolean();
            AtomicBoolean admin = new AtomicBoolean(true);
            AuthenticationProvider auth = header -> new UserContext("u", admin.get() ? Set.of("admin") : Set.of(), Map.of());
            var session = new GraphQLWebSocketSession("c", new BoosterGraphQL(app), auth, messages::add, () -> closed.set(true));
            session.receive("{\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer fixture\"}}");
            admin.set(false); session.tick();
            assertTrue(closed.get()); assertTrue(session.isClosed());
            var clock = new MutableClock();
            var restored = new GraphQLWebSocketSession("persisted", (op, ctx) -> new BoosterGraphQL(app).execute(op.query(), op.variables(), op.operationName(), ctx),
                    auth, messages::add, () -> {}, clock, Duration.ofHours(1), new GraphQLWebSocketSession.Listener(){});
            restored.restore(UserContext.anonymous(), clock.instant().plusSeconds(5));
            restored.receive("{\"id\":\"query\",\"type\":\"start\",\"payload\":{\"query\":\"{health}\"}}");
            assertFalse(restored.isClosed()); clock.now = clock.now.plusSeconds(6); restored.tick(); assertTrue(restored.isClosed());
        }
    }

    @Test void sharedListenerMountPreservesHeadersBodyAndNestedRoute() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0); var client = HttpClient.newHttpClient()) {
            server.route("/webhook", exchange -> {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("X-Route", exchange.getRequestURI().getRawPath());
                exchange.sendResponseHeaders(201, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
            var response = client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/webhook/nested?x=1"))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("payload")).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode()); assertEquals("payload", response.body());
            assertEquals("/webhook/nested", response.headers().firstValue("X-Route").orElseThrow());
        }
    }

    @Test void fractionalTokenExpiryUsesBaselineNumericDateAndPersistsTheEffectiveDeadline() {
        var clock = new MutableClock(); var expires = new java.util.concurrent.atomic.AtomicReference<Instant>();
        var user = new UserContext(null, Set.of(), Map.of("exp", clock.instant().getEpochSecond() + 0.5), Map.of(), true);
        try (var session = new GraphQLWebSocketSession("fractional", (op, context) -> { throw new AssertionError(); },
                header -> user, message -> {}, () -> {}, clock, Duration.ofHours(2), new GraphQLWebSocketSession.Listener() {
                    @Override public void initialized(UserContext identity, Instant deadline) { expires.set(deadline); }
                })) {
            session.receive("{\"type\":\"connection_init\"}");
            assertFalse(session.isClosed()); assertEquals(clock.instant().plusSeconds(1), expires.get());
            clock.now = clock.now.plusMillis(750); session.tick(); assertFalse(session.isClosed());
            clock.now = clock.now.plusMillis(250); session.tick(); assertTrue(session.isClosed());
        }
    }

    @Test void realWebSocketHeartbeatClosesConnectionAfterRoleRevocation() throws Exception {
        AtomicBoolean permitted = new AtomicBoolean(true);
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0).authentication(header -> new UserContext("u", permitted.get() ? Set.of("reader") : Set.of(), Map.of()));
             var client = HttpClient.newHttpClient()) {
            server.start(); var messages = new Messages();
            var socket = client.newWebSocketBuilder().subprotocols("graphql-ws")
                    .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/graphql"), messages).join();
            send(socket, Map.of("type", "connection_init")); messages.next();
            permitted.set(false);
            assertEquals("connection_error", messages.next().path("type").asText());
            assertEquals(1000, messages.closed.get(5, TimeUnit.SECONDS));
        }
    }

    @Test void completedOperationIdsCanBeReusedWithoutReplayingAnEarlierMutation() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0); var client = HttpClient.newHttpClient()) {
            server.start(); var messages = new Messages();
            var socket = client.newWebSocketBuilder().subprotocols("graphql-ws")
                    .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/graphql"), messages).join();
            send(socket, Map.of("type", "connection_init")); messages.next();
            for (String cart : List.of("first", "second")) {
                send(socket, Map.of("id", "reused", "type", "start", "payload", Map.of("query",
                        "mutation Open($input:OpenCartInput!){OpenCart(input:$input){id}}", "variables", Map.of("input", Map.of("cartId", cart)))));
                var result = messages.next(); assertFalse(result.path("payload").has("errors"), result.toString());
                assertEquals(cart, result.at("/payload/data/OpenCart/id").asText()); messages.next();
            }
            assertEquals(2, app.eventStore().lastPosition()); socket.sendClose(1000, "done").join();
        }
    }

    @Test void oneSlowConnectionDoesNotDelayOtherConnectionsAuthenticationHeartbeat() throws Exception {
        var slowCalls = new java.util.concurrent.atomic.AtomicInteger();
        var checkingSlow = new CountDownLatch(1); var releaseSlow = new CountDownLatch(1); var allowed = new AtomicBoolean(true);
        AuthenticationProvider provider = header -> {
            if ("Bearer slow".equals(header) && slowCalls.incrementAndGet() > 1) {
                checkingSlow.countDown();
                try { if (!releaseSlow.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
            return new UserContext("u", "Bearer slow".equals(header) || allowed.get() ? Set.of("reader") : Set.of(), Map.of());
        };
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0).authentication(provider); var client = HttpClient.newHttpClient()) {
            try {
                server.start(); var slowMessages = new Messages(); var fastMessages = new Messages();
                URI address = URI.create("ws://127.0.0.1:" + server.port() + "/graphql");
                var slow = client.newWebSocketBuilder().subprotocols("graphql-ws").buildAsync(address, slowMessages).join();
                send(slow, Map.of("type", "connection_init", "payload", Map.of("Authorization", "Bearer slow"))); slowMessages.next();
                assertTrue(checkingSlow.await(5, TimeUnit.SECONDS));
                var fast = client.newWebSocketBuilder().subprotocols("graphql-ws").buildAsync(address, fastMessages).join();
                send(fast, Map.of("type", "connection_init")); fastMessages.next(); allowed.set(false);
                assertEquals("connection_error", fastMessages.next().path("type").asText());
                assertEquals(1000, fastMessages.closed.get(5, TimeUnit.SECONDS));
                releaseSlow.countDown(); slow.sendClose(1000, "done").join();
            } finally { releaseSlow.countDown(); }
        }
    }

    private static void send(WebSocket socket, Object value) throws Exception { socket.sendText(Json.MAPPER.writeValueAsString(value), true).join(); }
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    private static final class Messages implements WebSocket.Listener {
        final BlockingQueue<String> values = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) { values.add(partial.toString()); partial.setLength(0); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) { closed.complete(statusCode); return null; }
        JsonNode next() throws Exception {
            String value = values.poll(5, TimeUnit.SECONDS); assertNotNull(value, "Expected WebSocket message"); return Json.MAPPER.readTree(value);
        }
    }
}
