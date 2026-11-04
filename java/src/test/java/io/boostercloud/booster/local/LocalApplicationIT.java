package io.boostercloud.booster.local;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Full local provider, real HTTP/WebSocket clients and two independent application JVMs. */
class LocalApplicationIT {
    @TempDir Path temporary;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private record Running(Process process, int port, LocalConfiguration config) implements AutoCloseable {
        public void close() throws Exception {
            try {
                if (process.isAlive()) LocalInfrastructure.stop(config);
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Local host did not stop");
                assertEquals(0, process.exitValue());
            } finally {
                if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            }
        }
    }

    private Running start(Path publicKey, boolean interruptMigration, String logName) throws Exception {
        Path data = temporary.resolve("data"), log = temporary.resolve(logName);
        var config = new LocalConfiguration(FullLocalApplication.class.getName(), data.toString(), "127.0.0.1", 0);
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), FullLocalApplication.class.getName(), data.toString(),
                publicKey.toString(), Boolean.toString(interruptMigration))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            var pattern = Pattern.compile("application started: http://127\\.0\\.0\\.1:(\\d+)");
            long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            while (System.nanoTime() < deadline) {
                var match = pattern.matcher(Files.readString(log));
                if (match.find()) return new Running(process, Integer.parseInt(match.group(1)), config);
                if (!process.isAlive()) fail("Application exited during startup:\n" + Files.readString(log));
                Thread.sleep(25);
            }
            throw new AssertionError("Application startup timed out:\n" + Files.readString(log));
        } catch (Exception | AssertionError error) {
            process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); throw error;
        }
    }

    @Test @Timeout(90)
    void authenticatedSubscriptionsRocketsMigrationsAndJoinedModelsSurviveARealProcessRestart() throws Exception {
        var keyGenerator = KeyPairGenerator.getInstance("RSA"); keyGenerator.initialize(2048);
        var key = keyGenerator.generateKeyPair();
        Path publicKey = temporary.resolve("public.der"); Files.write(publicKey, key.getPublic().getEncoded());
        String token = token(key.getPrivate(), Instant.now().plusSeconds(300));
        String expired = token(key.getPrivate(), Instant.now().minusSeconds(10));
        String placeFirst = "mutation {PlaceOrder(input:{id:\"order-1\",customerId:\"customer-old\",customerName:\"Ada\",totalCents:1250}){id}}";
        String placeSecond = "mutation {PlaceOrder(input:{id:\"order-2\",customerId:\"customer-old\",customerName:\"Ada\",totalCents:2500}){id}}";
        String noteBody = "{\"id\":\"note-1\",\"text\":\"Webhook received\"}";
        byte[] file = new byte[]{0, 1, -1, 7, 21};

        try (var host = start(publicKey, true, "first.log")) {
            assertEquals(host.port(), LocalInfrastructure.status(host.config()).path("port").asInt());
            assertTrue(graphqlResponse(host.port(), "{OrderView(id:\"order-1\"){id}}", null, null).has("errors"));
            assertEquals(401, request(host.port(), "/graphql", "{\"query\":\"{health}\"}", expired, null).statusCode());

            var messages = new Messages();
            WebSocket socket = http.newWebSocketBuilder().subprotocols("graphql-ws").connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create("ws://127.0.0.1:" + host.port() + "/graphql"), messages).get(5, TimeUnit.SECONDS);
            try {
                send(socket, Map.of("type", "connection_init", "payload", Map.of("Authorization", "Bearer " + token)));
                messages.await(message -> message.path("type").asText().equals("connection_ack"));
                send(socket, Map.of("id", "orders", "type", "start", "payload", Map.of("query", "subscription {OrderView(id:\"order-1\"){id customerName totalCents}}")));
                send(socket, Map.of("id", "barrier", "type", "start", "payload", Map.of("query", "{health}")));
                messages.await(message -> message.path("id").asText().equals("barrier") && message.path("type").asText().equals("complete"));
                graphql(host.port(), placeFirst, token, "seed-order-1");
                JsonNode change = messages.await(message -> message.at("/payload/data/OrderView/customerName").asText().equals("Ada"));
                assertEquals(1250, change.at("/payload/data/OrderView/totalCents").asLong());
                send(socket, Map.of("id", "orders", "type", "stop"));
                messages.await(message -> message.path("id").asText().equals("orders") && message.path("type").asText().equals("complete"));
            } finally { socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS); }

            graphql(host.port(), placeSecond, token, "seed-order-2");
            assertEquals(401, request(host.port(), "/webhook/incoming/note", noteBody, expired, "note-delivery").statusCode());
            assertEquals(200, request(host.port(), "/webhook/incoming/note", noteBody, token, "note-delivery").statusCode());
            assertEquals(200, request(host.port(), "/webhook/incoming/note", noteBody, token, "note-delivery").statusCode());
            assertEquals(1, graphql(host.port(), "{NoteView(id:\"note-1\"){deliveries}}", token, null).at("/NoteView/deliveries").asInt());

            URI upload = URI.create(graphql(host.port(), "mutation {UploadUrl(input:{directory:\"incoming\",fileName:\"file.bin\"})}", token, "upload-url")
                    .path("UploadUrl").asText());
            assertEquals(200, put(upload, file).statusCode()); assertEquals(200, put(upload, file).statusCode());
            assertFiles(host.port(), token, file);
            JsonNode failed = graphqlResponse(host.port(), "mutation {RunMigrations(input:{resume:false})}", token, "start-migrations");
            assertTrue(failed.has("errors"), failed.toString());
            JsonNode status = graphql(host.port(), "{MigrationStates}", token, null).path("MigrationStates").get(0);
            assertEquals("FAILED", status.at("/lifecycle/status").asText());
            assertTrue(status.at("/checkpoint/firstOrderDone").asBoolean());
        }

        try (var host = start(publicKey, false, "second.log")) {
            assertFiles(host.port(), token, file);
            assertEquals(200, request(host.port(), "/webhook/incoming/note", noteBody, token, "note-delivery").statusCode());
            assertEquals(1, graphql(host.port(), "{NoteView(id:\"note-1\"){deliveries}}", token, null).at("/NoteView/deliveries").asInt());
            graphql(host.port(), "mutation {RunMigrations(input:{resume:true})}", token, "resume-migrations");
            JsonNode statuses = graphql(host.port(), "{MigrationStates}", token, null).path("MigrationStates");
            assertEquals(2, statuses.size());
            for (var status : statuses) assertEquals("FINISHED", status.at("/lifecycle/status").asText());
            assertEquals(2, statuses.get(0).at("/lifecycle/attempts").asInt());
            JsonNode orders = graphql(host.port(), "{ListOrderViews{items{id customerId customerName segment totalCents} count}}", token, null).path("ListOrderViews");
            assertEquals(2, orders.path("items").size());
            for (var order : orders.path("items")) {
                assertEquals("customer-new", order.path("customerId").asText());
                assertEquals("Ada Lovelace", order.path("customerName").asText());
                assertEquals("gold", order.path("segment").asText());
            }
        }
        var config = new LocalConfiguration(FullLocalApplication.class.getName(), temporary.resolve("data").toString(), "127.0.0.1", 0);
        LocalInfrastructure.nuke(config, config.directory());
        assertFalse(Files.exists(config.directory()));
    }

    private void assertFiles(int port, String token, byte[] content) throws Exception {
        String url = graphql(port, "{DownloadUrl(input:{directory:\"incoming\",fileName:\"file.bin\"})}", token, null).path("DownloadUrl").asText();
        var response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode()); assertArrayEquals(content, response.body());
        JsonNode listing = graphql(port, "{ListFiles(input:{directory:\"incoming\"})}", token, null).path("ListFiles");
        assertEquals(1, listing.size()); assertEquals("incoming/file.bin", listing.get(0).path("name").asText());
        assertEquals(content.length, listing.get(0).at("/properties/contentLength").asInt());
        JsonNode models = graphql(port, "{ListUploadViews{items{id path contentLength} count}}", token, null).path("ListUploadViews");
        assertEquals(1, models.path("items").size()); assertEquals("assets/incoming/file.bin", models.path("items").get(0).path("path").asText());
    }
    private HttpResponse<String> put(URI uri, byte[] body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri).header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode graphql(int port, String query, String token, String id) throws Exception {
        JsonNode response = graphqlResponse(port, query, token, id);
        assertFalse(response.has("errors"), response.toString()); return response.path("data");
    }
    private JsonNode graphqlResponse(int port, String query, String token, String id) throws Exception {
        var response = request(port, "/graphql", Json.MAPPER.writeValueAsString(Map.of("query", query)), token, id);
        assertEquals(200, response.statusCode(), response.body()); return Json.MAPPER.readTree(response.body());
    }
    private HttpResponse<String> request(int port, String path, String body, String token, String id) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (id != null) builder.header("Idempotency-Key", id);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String token(PrivateKey key, Instant expires) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String header = base64.encodeToString(Json.MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String claims = base64.encodeToString(Json.MAPPER.writeValueAsBytes(Map.of("iss", "local-fixture", "aud", "booster-local",
                "sub", "operator-1", "exp", expires.getEpochSecond(), "custom:role", List.of("operator", "reader"))));
        String unsigned = header + "." + claims;
        var signer = Signature.getInstance("SHA256withRSA"); signer.initSign(key); signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + base64.encodeToString(signer.sign());
    }
    private static void send(WebSocket socket, Object message) throws Exception {
        socket.sendText(Json.MAPPER.writeValueAsString(message), true).get(5, TimeUnit.SECONDS);
    }
    private static final class Messages implements WebSocket.Listener {
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final StringBuilder pending = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            pending.append(text); if (last) { received.add(pending.toString()); pending.setLength(0); }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        JsonNode await(Predicate<JsonNode> predicate) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            while (System.nanoTime() < deadline) {
                String text = received.poll(100, TimeUnit.MILLISECONDS);
                if (text != null) {
                    JsonNode message = Json.MAPPER.readTree(text);
                    assertFalse(message.path("type").asText().equals("error") || message.path("type").asText().equals("connection_error"), text);
                    if (predicate.test(message)) return message;
                }
            }
            throw new AssertionError("Expected WebSocket message not received");
        }
    }
}
