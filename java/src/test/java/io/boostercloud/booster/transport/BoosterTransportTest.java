package io.boostercloud.booster.transport;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import org.junit.jupiter.api.Test;
import graphql.ExecutionResult;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BoosterTransportTest {
    private static final String OPEN = "mutation Open($input: OpenCartInput!) { OpenCart(input: $input) { id } }";
    private static final String ADD = "mutation { AddItem(input: {cartId: \"cart\", sku: \"book\", quantity: 2, unitPriceCents: 1299}) {id} }";
    private static final String VIEW = "query { CartView(id: \"cart\") { ...CartFields } } fragment CartFields on CartView { id totalCents totalQuantity }";

    @Test void realHttpGraphQLExecutesTypedCommandsVariablesFragmentsAndProjections() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var opened = post(client, server, OPEN, Map.of("input", Map.of("cartId", "cart")), "open-cart");
            assertEquals(200, opened.statusCode());
            assertEquals("cart", json(opened).path("data").path("OpenCart").path("id").asText());
            var added = post(client, server, ADD, Map.of(), "add-book");
            assertFalse(json(added).has("errors"), added.body());
            assertEquals(added.body(), post(client, server, ADD, Map.of(), "add-book").body());
            var view = json(post(client, server, VIEW, Map.of(), null));
            assertEquals(2598, view.path("data").path("CartView").path("totalCents").asLong());
            assertEquals(2, view.path("data").path("CartView").path("totalQuantity").asInt());
            assertEquals(2, app.eventStore().lastPosition());
            var health = client.send(HttpRequest.newBuilder(uri(server, "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("UP", json(health).path("status").asText());
        }
    }

    @Test void listSupportsFilterSortAndCursor() {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory())) {
            var api = new BoosterGraphQL(app);
            for (String id : List.of("b", "a", "c")) {
                var result = api.execute(OPEN, Map.of("input", Map.of("cartId", id)), null, RequestContext.anonymous());
                assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            }
            String query = "query Page($after:String) { ListCartViews(filter:{checkedOut:{eq:false}},sortBy:{id:ASC},limit:2,afterCursor:$after) { items {id} count cursor } }";
            JsonNode first = Json.MAPPER.valueToTree(api.execute(query, Map.of(), "Page", RequestContext.anonymous()).toSpecification());
            assertFalse(first.has("errors"), first.toString());
            assertEquals("a", first.at("/data/ListCartViews/items/0/id").asText());
            assertEquals("b", first.at("/data/ListCartViews/items/1/id").asText());
            String cursor = first.at("/data/ListCartViews/cursor").asText();
            assertFalse(cursor.isBlank());
            JsonNode second = Json.MAPPER.valueToTree(api.execute(query, Map.of("after", cursor), "Page", RequestContext.anonymous()).toSpecification());
            assertEquals("c", second.at("/data/ListCartViews/items/0/id").asText());
        }
    }

    @Test void graphqlValidationPreventsExecutionAndErrorsHidePrivateDetails() {
        record SecretCommand(String value) {}
        try (var app = Booster.builder(JdbcEventStore.inMemory())
                .command(SecretCommand.class, String.class, AccessPolicy.publicAccess(), (command, register) -> {
                    throw new IllegalStateException("database-password=secret");
                }).build()) {
            var api = new BoosterGraphQL(app);
            var invalid = api.execute("mutation { SecretCommand(input: {wrong: \"value\"}) }", Map.of(), null, RequestContext.anonymous());
            assertFalse(invalid.getErrors().isEmpty());
            var failed = api.execute("mutation { SecretCommand(input: {value: \"value\"}) }", Map.of(), null, RequestContext.anonymous());
            assertFalse(failed.getErrors().isEmpty());
            assertEquals("Operation failed", failed.getErrors().getFirst().getMessage());
            assertFalse(failed.toSpecification().toString().contains("password"));
            assertEquals(0, app.eventStore().lastPosition());
        }
    }

    public record AdminCommand(String value) {}
    @Test void authorizationRequiresTrustedAuthenticatedIdentityAndDeniesSpoofedHeaders() throws Exception {
        try (var app = Booster.builder(JdbcEventStore.inMemory())
                .command(AdminCommand.class, String.class, AccessPolicy.roles("admin"), (command, register) -> command.value()).build();
             var server = new BoosterHttpServer(app, 0).authentication(header -> {
                 if (header == null) return UserContext.anonymous();
                 if (!header.equals("Bearer valid")) throw new AuthenticationException();
                 return new UserContext("alice", Set.of("admin"), Map.of());
             }); var client = HttpClient.newHttpClient()) {
            server.start();
            String query = "mutation { AdminCommand(input:{value:\"allowed\"}) }";
            String body = Json.MAPPER.writeValueAsString(Map.of("query", query));
            var spoofed = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).header("Content-Type", "application/json")
                    .header("X-User-Roles", "admin").header("X-User", "alice").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("FORBIDDEN", json(spoofed).at("/errors/0/extensions/code").asText());
            var allowed = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer valid").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("allowed", json(allowed).at("/data/AdminCommand").asText());
            var invalid = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer invalid").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, invalid.statusCode());
        }
    }

    @Test void malformedOversizedAndUnsupportedHttpRequestsAreRejected() throws Exception {
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            for (String body : List.of("not JSON", "{}", "{\"query\":\"{health}\"} {}", "{\"query\":\"{health}\",\"query\":\"{health}\"}")) {
                var response = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(400, response.statusCode(), body);
            }
            var oversized = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(" ".repeat(1_048_577))).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(413, oversized.statusCode());
            var wrongMethod = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, wrongMethod.statusCode());
            var unsupported = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, unsupported.statusCode());
            var invalidCredentials = client.send(HttpRequest.newBuilder(uri(server, "/graphql")).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ignored?").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{health}\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, invalidCredentials.statusCode());
        }
    }

    @Test void subscriptionStreamsProjectionUpdatesAndStopsWhenAuthenticationExpires() throws Exception {
        AtomicBoolean valid = new AtomicBoolean(true);
        try (var app = ShoppingCartApplication.create(JdbcEventStore.inMemory());
             var server = new BoosterHttpServer(app, 0).authentication(header -> {
                 if (!valid.get()) throw new AuthenticationException();
                 return UserContext.anonymous();
             }); var client = HttpClient.newHttpClient(); var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.start();
            post(client, server, OPEN, Map.of("input", Map.of("cartId", "cart")), null);
            String payload = Json.MAPPER.writeValueAsString(Map.of("query", "subscription { CartView(id:\"cart\") {id totalQuantity} }"));
            var stream = client.send(HttpRequest.newBuilder(uri(server, "/graphql/stream")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, stream.statusCode());
            try (var body = stream.body(); var reader = new BufferedReader(new InputStreamReader(body))) {
                assertEquals(": connected", reader.readLine());
                Future<String> next = readers.submit(() -> readEvent(reader, "event: next"));
                post(client, server, ADD, Map.of(), null);
                String event = next.get(10, TimeUnit.SECONDS);
                assertEquals(2, Json.MAPPER.readTree(event).at("/data/CartView/totalQuantity").asInt());
                Future<String> expired = readers.submit(() -> readEvent(reader, "event: error"));
                valid.set(false);
                app.executeCommand("AddItem", Json.MAPPER.valueToTree(new ShoppingCartApplication.AddItem("cart", "book", 1, 1299)), RequestContext.anonymous());
                assertTrue(expired.get(10, TimeUnit.SECONDS).contains("Authentication expired"));
            }
        }
    }

    public record Life(String id) {}
    public record LiveView(String id) {}
    public record ChangeLife(String id, boolean present) {}
    public record LifeChanged(String id, boolean present) {}

    @Test void subscriptionDeliversDeletionAsNullableGraphQLData() throws Exception {
        try (var app = Booster.builder(JdbcEventStore.inMemory()).entity(Life.class)
                .event(LifeChanged.class, LifeChanged::id, Life.class, (event, old) -> event.present() ? new Life(event.id()) : null)
                .command(ChangeLife.class, Void.class, AccessPolicy.publicAccess(), (command, register) -> {
                    register.emit(new LifeChanged(command.id(), command.present())); return null;
                })
                .readModel(LiveView.class, Life.class, AccessPolicy.publicAccess(), (state, previous) -> state == null ? null : new LiveView(state.id()))
                .build()) {
            app.executeCommand("ChangeLife", Json.MAPPER.valueToTree(new ChangeLife("one", true)), RequestContext.anonymous());
            var result = new BoosterGraphQL(app).execute("subscription { LiveView(id:\"one\") { id } }", Map.of(), null, RequestContext.anonymous());
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            Publisher<ExecutionResult> publisher = result.getData();
            CompletableFuture<ExecutionResult> delivered = new CompletableFuture<>();
            CountDownLatch subscribed = new CountDownLatch(1);
            publisher.subscribe(new Subscriber<>() {
                private Subscription subscription;
                @Override public void onSubscribe(Subscription value) { subscription = value; value.request(1); subscribed.countDown(); }
                @Override public void onNext(ExecutionResult value) { delivered.complete(value); subscription.cancel(); }
                @Override public void onError(Throwable error) { delivered.completeExceptionally(error); }
                @Override public void onComplete() {}
            });
            assertTrue(subscribed.await(5, TimeUnit.SECONDS));
            app.executeCommand("ChangeLife", Json.MAPPER.valueToTree(new ChangeLife("one", false)), RequestContext.anonymous());
            JsonNode response = Json.MAPPER.valueToTree(delivered.get(5, TimeUnit.SECONDS).toSpecification());
            assertFalse(response.has("errors"), response.toString());
            assertTrue(response.at("/data/LiveView").isNull(), response.toString());
        }
    }

    private static String readEvent(BufferedReader reader, String expected) throws Exception {
        boolean found = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(expected)) found = true;
            else if (found && line.startsWith("data: ")) return line.substring(6);
        }
        throw new AssertionError("Stream ended before " + expected);
    }

    @Test @org.junit.jupiter.api.Timeout(20)
    void subscriptionEndsWhenAuthenticatorRevokesItsAuthorizedRole() throws Exception {
        var identity = new java.util.concurrent.atomic.AtomicReference<>(
                new UserContext("alice", Set.of("reader"), Map.of()));
        try (var app = Booster.builder(JdbcEventStore.inMemory()).entity(Life.class)
                .event(LifeChanged.class, LifeChanged::id, Life.class,
                        (event, previous) -> event.present() ? new Life(event.id()) : null)
                .command(ChangeLife.class, Void.class, AccessPolicy.publicAccess(), (command, register) -> {
                    register.emit(new LifeChanged(command.id(), command.present())); return null;
                })
                .readModel(LiveView.class, Life.class, AccessPolicy.roles("reader"),
                        (state, previous) -> state == null ? null : new LiveView(state.id()))
                .build();
             var server = new BoosterHttpServer(app, 0).authentication(header -> identity.get());
             var client = HttpClient.newHttpClient(); var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.start();
            app.executeCommand("ChangeLife", Json.MAPPER.valueToTree(new ChangeLife("one", true)), RequestContext.anonymous());
            String payload = Json.MAPPER.writeValueAsString(Map.of("query", "subscription { LiveView(id:\"one\") { id } }"));
            var response = client.send(HttpRequest.newBuilder(uri(server, "/graphql/stream"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());
            try (var body = response.body(); var reader = new BufferedReader(new InputStreamReader(body))) {
                assertEquals(": connected", reader.readLine());
                Future<String> rejected = readers.submit(() -> readEvent(reader, "event: error"));
                identity.set(new UserContext("alice", Set.of(), Map.of()));
                app.executeCommand("ChangeLife", Json.MAPPER.valueToTree(new ChangeLife("one", false)), RequestContext.anonymous());
                assertTrue(rejected.get(10, TimeUnit.SECONDS).contains("Authentication expired"));
            }
        }
    }
    private static URI uri(BoosterHttpServer server, String path) { return URI.create("http://127.0.0.1:" + server.port() + path); }
    private static HttpResponse<String> post(HttpClient client, BoosterHttpServer server, String query,
                                              Map<String, Object> variables, String key) throws Exception {
        var request = HttpRequest.newBuilder(uri(server, "/graphql")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                        Json.MAPPER.writeValueAsString(Map.of("query", query, "variables", variables))));
        if (key != null) request.header("Idempotency-Key", key);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static JsonNode json(HttpResponse<String> response) throws Exception { return Json.MAPPER.readTree(response.body()); }
}
