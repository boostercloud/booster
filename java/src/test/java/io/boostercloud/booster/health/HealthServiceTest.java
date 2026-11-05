package io.boostercloud.booster.health;

import com.sun.net.httpserver.HttpServer;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.store.JdbcStateStore;
import io.boostercloud.booster.transport.AuthenticationProvider;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HealthServiceTest {
  private static HealthRequest request(String path) { return new HealthRequest(path, UserContext.anonymous(), Map.of()); }
  private static HealthConfiguration sensor(String id, boolean enabled, boolean details, boolean children) {
    return new HealthConfiguration(id, id, enabled, details, children);
  }

  @Test void parentEnablementDetailsAndChildVisibilityAreInherited() {
    var calls = new AtomicInteger();
    try (var service = HealthService.builder()
        .indicator(sensor("visible", true, false, false), (config, request) -> HealthResult.of(HealthStatus.UP, Map.of("secret", "hidden")))
        .indicator(sensor("visible/child", true, true, true), (config, request) -> { calls.incrementAndGet(); return new HealthResult(HealthStatus.UP); })
        .indicator(sensor("visible/child/grandchild", true, true, true), (config, request) -> { calls.incrementAndGet(); return new HealthResult(HealthStatus.UP); })
        .indicator(sensor("disabled", false, true, true), (config, request) -> { fail("Disabled parent must not run"); return null; })
        .indicator(sensor("disabled/child", true, true, true), (config, request) -> { fail("Disabled ancestor must suppress child"); return null; })
        .build()) {
      var root = service.check(request(""));
      assertTrue(root.healthy()); assertEquals(1, root.components().size());
      assertNull(root.components().getFirst().details());
      assertNull(root.components().getFirst().components()); assertEquals(0, calls.get());
      var child = service.check(request("visible/child"));
      assertEquals(1, child.components().size());
      assertNull(child.components().getFirst().components(), "Ancestor's showChildren applies to descendant selection");
      assertEquals(1, calls.get());
      assertTrue(service.check(request("disabled/child")).components().isEmpty());
      assertEquals(BoosterException.Code.NOT_FOUND,
          assertThrows(BoosterException.class, () -> service.check(request("missing"))).code());
    }
  }

  @Test void childFailureChangesOverallStatusAndUnknownRocketsAreTheOnlyException() {
    try (var service = HealthService.builder()
        .indicator(sensor("booster", true, true, true), (config, request) -> new HealthResult(HealthStatus.UP))
        .indicator(sensor("booster/database", true, true, true), (config, request) -> new HealthResult(HealthStatus.DOWN))
        .indicator(sensor("rockets", true, true, true), (config, request) -> new HealthResult(HealthStatus.UNKNOWN))
        .indicator(sensor("custom", true, true, true), (config, request) -> new HealthResult(HealthStatus.UNKNOWN))
        .build()) {
      assertFalse(service.check(request("")).healthy());
      assertTrue(service.check(request("rockets")).healthy());
      assertFalse(service.check(request("custom")).healthy());
      assertFalse(service.check(request("booster")).healthy());
    }
  }

  @Test void failedChecksAreSanitizedAndCustomAuthorizerRunsBeforeAnySensor() {
    var calls = new AtomicInteger();
    var authorized = new UserContext("operator", Set.of("health"), Map.of());
    try (var service = HealthService.builder()
        .authorize(request -> request.user().roles().contains("health") && !request.componentPath().equals("private"))
        .indicator(sensor("booster", true, true, true), (config, request) -> {
          calls.incrementAndGet(); throw new IllegalStateException("password=must-not-appear");
        }).build()) {
      assertEquals(BoosterException.Code.FORBIDDEN, assertThrows(BoosterException.class, () -> service.check(request(""))).code());
      assertEquals(0, calls.get());
      var report = service.check(new HealthRequest("booster", authorized, Map.of()));
      assertFalse(report.healthy()); assertEquals(1, calls.get());
      assertEquals("Health check failed", report.components().getFirst().details().path("error").asText());
      assertFalse(Json.MAPPER.valueToTree(report.components()).toString().contains("must-not-appear"));
    }
  }

  @Test void policyAdapterPreservesTrustedRequestMetadataAndComponentInput() {
    var user = new UserContext("operator", Set.of("health"), Map.of("tenant", "one"));
    var pathPolicy = HealthAuthorizer.policy(AccessPolicy.input((context, input) ->
        context.user().authenticated() && context.metadata().getOrDefault("transport", "").equals("http")
            && input.path("componentPath").asText().equals("booster/database")));
    assertTrue(pathPolicy.permits(new HealthRequest("booster/database", user, Map.of("transport", "http"))));
    assertFalse(pathPolicy.permits(new HealthRequest("booster", user, Map.of("transport", "http"))));
    assertFalse(pathPolicy.permits(new HealthRequest("booster/database", user, Map.of("transport", "untrusted"))));
    var contextPolicy = HealthAuthorizer.policy(AccessPolicy.request(context ->
        "one".equals(context.user().claims().get("tenant")) && "http".equals(context.metadata().get("transport"))));
    assertTrue(contextPolicy.permits(new HealthRequest("booster", user, Map.of("transport", "http"))));
    assertFalse(contextPolicy.permits(new HealthRequest("booster", UserContext.anonymous(), Map.of("transport", "http"))));
  }

  @Test void timeoutDoesNotReleaseCapacityWhileSensorIgnoresInterruption() throws Exception {
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try (var service = HealthService.builder().timeout(Duration.ofMillis(100)).concurrency(1)
        .indicator(sensor("slow", true, true, true), (config, request) -> {
          calls.incrementAndGet();
          try {
            while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) { }
            return new HealthResult(HealthStatus.UP);
          } finally { finished.countDown(); }
        }).build()) {
      try {
        var report = service.check(request("slow"));
        assertEquals("Health check timed out", report.components().getFirst().details().path("error").asText());
        assertEquals("Health check capacity reached", service.check(request("slow")).components().getFirst().details().path("error").asText());
        assertEquals(1, calls.get());
      } finally { release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS)); }
    }
  }

  @Test void providerDefaultsReadActualStoresAndRemainDisabledUntilEnabled() {
    var booster = Booster.builder(JdbcEventStore.inMemory()).stateStore(JdbcStateStore.inMemory()).build();
    try (var disabled = ProviderHealth.defaults(booster, () -> true, () -> "http://127.0.0.1/graphql", List.of("local"), Map.of()).build()) {
      assertTrue(disabled.check(request("")).components().isEmpty());
    }
    try (var service = ProviderHealth.defaults(booster, () -> true, () -> "http://127.0.0.1/graphql", List.of("local"),
        Map.of("uploads", () -> true, "webhook", () -> false)).configureAll(true, true, true).build()) {
      assertTrue(service.check(request("booster")).healthy());
      var root = service.check(request("rockets"));
      assertEquals(HealthStatus.PARTIALLY_UP, root.components().getFirst().status());
      assertEquals(2, root.components().getFirst().components().size());
      assertTrue(service.check(request("rockets/uploads")).healthy());
      assertFalse(service.check(request("rockets/webhook")).healthy());
      booster.close();
      assertFalse(service.check(request("booster/database/events")).healthy());
      assertFalse(service.check(request("booster/database/readmodels")).healthy());
    } finally { booster.close(); }
  }

  @Test void realHttpUsesBaselineArrayAndStatusCodesAndRejectsInvalidAuthentication() throws Exception {
    AuthenticationProvider authentication = header -> {
      if (header == null) return UserContext.anonymous();
      if (!header.equals("Bearer verified")) throw new AuthenticationException();
      return new UserContext("reader", Set.of("health"), Map.of());
    };
    try (var service = HealthService.builder().authorize(HealthAuthorizer.policy(AccessPolicy.roles("health")))
        .indicator(sensor("booster", true, false, true), (config, request) -> new HealthResult(HealthStatus.UP))
        .indicator(sensor("broken", true, true, true), (config, request) -> new HealthResult(HealthStatus.DOWN))
        .build(); var executor = Executors.newVirtualThreadPerTaskExecutor(); var client = HttpClient.newHttpClient()) {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
      server.setExecutor(executor); server.createContext("/health", new HealthHttpHandler(service, authentication)); server.start();
      String origin = "http://127.0.0.1:" + server.getAddress().getPort();
      try {
        var root = client.send(HttpRequest.newBuilder(URI.create(origin + "/health")).header("Authorization", "Bearer verified").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, root.statusCode());
        assertTrue(Json.MAPPER.readTree(root.body()).isArray());
        var child = client.send(HttpRequest.newBuilder(URI.create(origin + "/health/booster")).header("Authorization", "Bearer verified").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, child.statusCode());
        assertEquals("booster", Json.MAPPER.readTree(child.body()).get(0).path("id").asText());
        assertFalse(Json.MAPPER.readTree(child.body()).get(0).has("details"));
        assertEquals("no-store", child.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(403, client.send(HttpRequest.newBuilder(URI.create(origin + "/health")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(origin + "/health")).header("Authorization", "Bearer forged").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(origin + "/health/missing")).header("Authorization", "Bearer verified").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(origin + "/health")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(400, client.send(HttpRequest.newBuilder(URI.create(origin + "/health/%2e%2e/private")).header("Authorization", "Bearer verified").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
      } finally { server.stop(0); }
    }
  }

  @Test void configurationRejectsAmbiguousTreesAndDetailsAreDefensiveCopies() {
    assertThrows(IllegalArgumentException.class, () -> sensor("parent/../child", true, true, true));
    assertThrows(IllegalArgumentException.class, () -> HealthService.builder()
        .indicator(sensor("missing/child", true, true, true), (config, request) -> new HealthResult(HealthStatus.UP)).build());
    var source = Json.MAPPER.createObjectNode().put("value", "original");
    var result = new HealthResult(HealthStatus.UP, source);
    source.put("value", "changed");
    ((com.fasterxml.jackson.databind.node.ObjectNode) result.details()).put("value", "changed-again");
    assertEquals("original", result.details().path("value").asText());
  }
}
