package io.boostercloud.booster.rockets.webhook;

import com.sun.net.httpserver.HttpServer;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class WebhookRocketTest {
  @TempDir Path temporary;
  record Received(String id, String message) {}
  record Receipt(String id, int count, String message) {}
  record ReceiptView(String id, int count, String message) {}
  record Ping() {}

  private static Booster runtime(EventStore store) {
    return Booster.builder(store).entity(Receipt.class)
        .event(Received.class, Received::id, Receipt.class,
            (event, previous) -> new Receipt(event.id(), previous == null ? 1 : previous.count() + 1, event.message()))
        .readModel(ReceiptView.class, Receipt.class, AccessPolicy.publicAccess(),
            (entity, old) -> entity == null ? null : new ReceiptView(entity.id(), entity.count(), entity.message())).build();
  }
  private static Map<String, List<String>> headers(String key) {
    return Map.of("Content-Type", List.of("application/json"), "Idempotency-Key", List.of(key));
  }
  private static WebhookResponse call(WebhookRocket rocket, String method, String path, String key, String body) {
    return rocket.dispatch(method, URI.create(path), headers(key), body.getBytes(StandardCharsets.UTF_8));
  }
  private static String text(WebhookResponse response) { return new String(response.body(), StandardCharsets.UTF_8); }

  @Test void nestedRoutePreservesRequestAndLegacyGetEventContract() throws Exception {
    try (var booster = runtime(new InMemoryEventStore())) {
      var request = new AtomicReference<WebhookRequest>();
      var rocket = WebhookRocket.builder(booster).route(new WebhookRoute("nested/orders", "old", Set.of("GET", "POST"),
          null, null, null, (input, register) -> { request.set(input); register.responseHeader("X-Handler", "yes"); return WebhookResponse.json(Map.of("ok", true)); })).build();
      WebhookResponse response = rocket.dispatch("GET", URI.create("/api/webhook/nested/orders?a=1&a=2&word=hola+amigo"),
          Map.of("X-Signature", List.of("signed")), Map.of("name", "orders"), new byte[0]);
      assertEquals(200, response.status()); assertEquals("{\"ok\":true}", text(response));
      assertEquals(List.of("yes"), response.headers().get("X-Handler"));
      assertEquals("nested/orders", request.get().route()); assertEquals("old", request.get().origin());
      assertEquals("POST", request.get().method()); assertEquals("GET", request.get().httpMethod());
      assertEquals(List.of("1", "2"), request.get().query().get("a"));
      assertEquals(List.of("hola amigo"), request.get().query().get("word"));
      assertEquals("orders", request.get().params().get("name"));
      assertEquals(List.of("signed"), request.get().headers().get("x-signature"));
      assertEquals("", request.get().rawBody());
      assertThrows(UnsupportedOperationException.class, () -> request.get().headers().put("a", List.of("b")));
    }
  }

  @Test void originFallbackAndDefaultPostAndFailureShapesMatchBaseline() throws Exception {
    try (var booster = runtime(new InMemoryEventStore())) {
      var rocket = WebhookRocket.builder(booster).route(new WebhookRoute(null, "webhook/old", null, null, null, null,
          (input, register) -> { assertEquals("webhook/old", input.route()); return null; })).build();
      assertEquals(200, call(rocket, "POST", "/webhook/old", "one", "").status());
      assertEquals("", text(call(rocket, "POST", "/webhook/old", "one", "")));
      WebhookResponse method = call(rocket, "GET", "/webhook/old", "two", "");
      assertEquals(500, method.status());
      assertEquals("Error", Json.MAPPER.readTree(method.body()).get("title").asText());
      assertEquals("Method GET not allowed", Json.MAPPER.readTree(method.body()).get("reason").asText());
      assertEquals(500, call(rocket, "POST", "/webhook/missing", "three", "").status());
    }
  }

  @Test void explicitStrictHttpModeRejectsMalformedJsonAndUsesActualMethod() {
    try (var booster = runtime(new InMemoryEventStore())) {
      var rocket = WebhookRocket.builder(booster).strictHttpSemantics(true)
          .route(new WebhookRoute("receive", null, Set.of("GET", "POST"), null, null, null,
              (input, register) -> WebhookResponse.text(input.method()))).build();
      assertEquals("GET", text(call(rocket, "GET", "/webhook/receive", "one", "")));
      assertEquals(400, call(rocket, "POST", "/webhook/receive", "two", "{broken").status());
      assertEquals(404, call(rocket, "POST", "/webhook/missing", "three", "{}").status());
      assertEquals(405, call(rocket, "DELETE", "/webhook/receive", "four", "{}").status());
    }
  }

  @Test void actionCommitsEventsAndResponseOnceWithConcurrentDeliveryAndChecksConflicts() throws Exception {
    try (var booster = runtime(new InMemoryEventStore()); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var invoked = new AtomicInteger();
      var rocket = WebhookRocket.builder(booster).route("receive", (input, register) -> {
        invoked.incrementAndGet(); register.emit(new Received("order-1", input.body().path("message").asText()));
        return WebhookResponse.text("received");
      }).build();
      List<Future<WebhookResponse>> results = new ArrayList<>();
      for (int i = 0; i < 8; i++) results.add(workers.submit(() -> call(rocket, "POST", "/webhook/receive", "delivery-1", "{\"message\":\"hello\"}")));
      for (Future<WebhookResponse> result : results) assertEquals("received", text(result.get(5, TimeUnit.SECONDS)));
      assertEquals(1, invoked.get()); assertEquals(1, booster.position());
      assertEquals(1, booster.getReadModel("ReceiptView", "order-1", RequestContext.anonymous()).path("count").asInt());
      assertEquals(500, call(rocket, "POST", "/webhook/receive", "delivery-1", "{\"message\":\"changed\"}").status());
      assertEquals(1, invoked.get());
    }
  }

  @Test void handlerFailureRollsBackBufferedEventsAndRetryCanSucceed() {
    try (var booster = runtime(new InMemoryEventStore())) {
      var attempts = new AtomicInteger();
      var rocket = WebhookRocket.builder(booster).route("receive", (input, register) -> {
        register.emit(new Received("r", "message"));
        if (attempts.incrementAndGet() == 1) throw new IllegalStateException("handler failed");
        return WebhookResponse.text("ok");
      }).build();
      assertEquals(500, call(rocket, "POST", "/webhook/receive", "retry", "{}").status()); assertEquals(0, booster.position());
      assertEquals(200, call(rocket, "POST", "/webhook/receive", "retry", "{}").status()); assertEquals(1, booster.position());
    }
  }

  @Test void rolesAndRequestAuthorizersRunBeforeEveryReplayWithoutPersistingBearerToken() {
    try (var booster = runtime(new InMemoryEventStore())) {
      var allowed = new AtomicBoolean(true); var authorizationCalls = new AtomicInteger(); var handlerCalls = new AtomicInteger();
      var rocket = WebhookRocket.builder(booster).authentication(header -> {
        if (!"Bearer token".equals(header)) throw new AuthenticationException();
        return new UserContext("u", allowed.get() ? Set.of("editor") : Set.of(), Map.of());
      }).route(new WebhookRoute("private", null, null, AccessPolicy.roles("editor"),
          (user, request) -> { authorizationCalls.incrementAndGet(); if (!request.query().containsKey("signed")) throw new BoosterException(BoosterException.Code.FORBIDDEN, "Signature required"); },
          null, (input, register) -> { handlerCalls.incrementAndGet(); register.emit(new Received("private", "ok")); return null; })).build();
      var headers = new HashMap<>(headers("auth-one")); headers.put("Authorization", List.of("Bearer token"));
      assertEquals(401, rocket.dispatch("POST", URI.create("/webhook/private"), headers, new byte[0]).status());
      assertEquals(200, rocket.dispatch("POST", URI.create("/webhook/private?signed=1"), headers, new byte[0]).status());
      assertEquals(200, rocket.dispatch("POST", URI.create("/webhook/private?signed=1"), headers, new byte[0]).status());
      allowed.set(false);
      assertEquals(401, rocket.dispatch("POST", URI.create("/webhook/private?signed=1"), headers, new byte[0]).status());
      assertEquals(1, handlerCalls.get()); assertEquals(4, authorizationCalls.get());
      assertFalse(booster.eventStore().readAll(0, 10).toString().contains("Bearer token"));
    }
  }

  @Test void requestAndResponseBoundsRejectBeforeCommit() {
    try (var booster = runtime(new InMemoryEventStore())) {
      var rocket = WebhookRocket.builder(booster).limits(8, 8, 1).route("bounded", (input, register) -> {
        register.emit(new Received("r", "too big")); return WebhookResponse.text("long response");
      }).build();
      assertEquals(413, call(rocket, "POST", "/webhook/bounded", "one", "a".repeat(9)).status());
      assertEquals(500, call(rocket, "POST", "/webhook/bounded", "two", "{}").status());
      assertEquals(0, booster.position());
      assertEquals(400, call(rocket, "POST", "/webhook/bounded", "invalid key", "{}").status());
      assertEquals(401, rocket.dispatch("POST", URI.create("/webhook/bounded"),
          Map.of("Authorization", List.of("Bearer a", "Bearer b")), new byte[0]).status());
    }
  }

  @Test void multipartArrivesInHandlerWithBinaryFilesAndNoBodyMutation() {
    try (var booster = runtime(new InMemoryEventStore())) {
      var rocket = WebhookRocket.builder(booster).route("upload", (input, register) -> {
        assertEquals("note", input.multipart().fields().getFirst().name());
        assertEquals("hello", input.multipart().fields().getFirst().value());
        assertEquals("file.bin", input.multipart().files().getFirst().filename());
        assertArrayEquals(new byte[] {0, (byte)255, 1}, input.multipart().files().getFirst().bufferFile());
        return WebhookResponse.file("result.bin", "application/octet-stream", input.multipart().files().getFirst().bufferFile());
      }).build();
      byte[] body = multipartBody("X", "../file.bin", new byte[] {0, (byte)255, 1});
      var response = rocket.dispatch("POST", URI.create("/webhook/upload"),
          Map.of("Content-Type", List.of("multipart/form-data; boundary=\"X\"")), body);
      assertEquals(200, response.status()); assertArrayEquals(new byte[] {0, (byte)255, 1}, response.body());
      assertEquals(List.of("attachment; filename=result.bin"), response.headers().get("Content-Disposition"));
    }
  }

  @Test void realHttpDeliverySurvivesServerAndDatabaseRestartWithOriginalResponse() throws Exception {
    Path path = temporary.resolve("journal");
    byte[] binary = new byte[] {0, 1, (byte)128, (byte)255};
    var calls = new AtomicInteger();
    for (int round = 0; round < 2; round++) {
      try (var booster = runtime(new JdbcEventStore(path))) {
        var rocket = WebhookRocket.builder(booster).route("persist", (input, register) -> {
          calls.incrementAndGet(); register.emit(new Received("persistent", "file"));
          return WebhookResponse.file("original.bin", "application/octet-stream", binary).withHeader("X-Result", "original");
        }).build();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 10);
        server.createContext("/webhook", rocket.handler()); server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
          URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook/persist");
          var request = HttpRequest.newBuilder(uri).header("Idempotency-Key", "persisted-delivery")
              .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"file\":true}")).build();
          var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
          assertEquals(200, response.statusCode()); assertArrayEquals(binary, response.body());
          assertEquals("original", response.headers().firstValue("X-Result").orElseThrow());
          assertEquals("persisted-delivery", response.headers().firstValue("X-Request-ID").orElseThrow());
          assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
          assertEquals(1, booster.getReadModel("ReceiptView", "persistent", RequestContext.anonymous()).path("count").asInt());
        } finally { server.stop(0); }
      }
    }
    assertEquals(1, calls.get());
  }

  @Test void responseAndRouteValidationRejectsAmbiguityAndHeaderInjection() {
    assertThrows(IllegalArgumentException.class, () -> WebhookResponse.text("x").withHeader("X-Test", "a\r\nb"));
    assertThrows(IllegalArgumentException.class, () -> WebhookResponse.text("x").withHeader("Content-Length", "2"));
    assertThrows(IllegalArgumentException.class, () -> new WebhookRoute("../outside", (a,b) -> null));
    try (var booster = runtime(new InMemoryEventStore())) {
      assertThrows(IllegalArgumentException.class, () -> WebhookRocket.builder(booster)
          .route("one", (a,b) -> null).route("webhook/one", (a,b) -> null));
    }
    assertEquals("", text(WebhookResponse.json(false))); assertEquals("", text(WebhookResponse.json(0)));
    assertEquals("null", text(WebhookResponse.text("null")));
  }

  @Test void responseVectorsMatchTheRecordedBaselineSourceContracts() throws Exception {
    try (var resource = getClass().getResourceAsStream("/webhook-baseline-contracts.json")) {
      assertNotNull(resource);
      var fixture = Json.MAPPER.readTree(resource);
      for (var vector : fixture.path("responses")) {
        Object value = Json.MAPPER.convertValue(vector.get("value"), Object.class);
        var response = switch (vector.get("type").asText()) {
          case "text" -> WebhookResponse.text((String)value);
          case "json" -> WebhookResponse.json(value);
          case "empty" -> WebhookResponse.empty();
          default -> throw new AssertionError("Unknown fixture type");
        };
        assertEquals(200, response.status());
        assertEquals(vector.get("body").asText(), text(response));
        assertEquals(List.of(vector.get("contentType").asText()), response.headers().get("Content-Type"));
        assertEquals(List.of("*"), response.headers().get("Access-Control-Allow-Origin"));
      }
    }
  }

  @Test void publicCommandsCannotPreclaimAWebhookReceiptNamespace() throws Exception {
    String receipt = "__booster_webhook:" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest("protected\npublic-key".getBytes(StandardCharsets.UTF_8)));
    try (var booster = Booster.builder(new InMemoryEventStore())
        .command(Ping.class, String.class, AccessPolicy.publicAccess(), (input, register) -> "pong").build()) {
      var rejected = assertThrows(BoosterException.class, () -> booster.executeCommand("Ping", Json.MAPPER.createObjectNode(),
          new RequestContext(receipt, UserContext.anonymous(), Map.of())));
      assertEquals(BoosterException.Code.VALIDATION, rejected.code()); assertTrue(booster.eventStore().findCommand(receipt).isEmpty());
      var rocket = WebhookRocket.builder(booster).route("protected", (input, register) -> WebhookResponse.text("original")).build();
      assertEquals("original", text(call(rocket, "POST", "/webhook/protected", "public-key", "{}")));
      assertTrue(booster.eventStore().findCommand(receipt).isPresent());
    }
  }

  static byte[] multipartBody(String boundary, String filename, byte[] file) {
    var output = new java.io.ByteArrayOutputStream();
    output.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nhello\r\n--" + boundary
        + "\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"" + filename
        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
    output.writeBytes(file); output.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    return output.toByteArray();
  }
}
