package io.boostercloud.booster.azure.infrastructure;

import com.azure.core.credential.AccessToken;
import com.sun.net.httpserver.HttpServer;
import io.boostercloud.booster.azure.hosting.UpstreamAuthentication;
import io.boostercloud.booster.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AzureInfrastructureTest {
  @TempDir Path directory;
  private AzureDeploymentConfig config() {
    return new AzureDeploymentConfig("test-app", "11111111-1111-1111-1111-111111111111", "isolated-java-test",
        "westeurope", "registry.example/application:revision", "com.example.Application", List.of("uploads", "images"), 1, 8080);
  }
  @Test void deterministicSynthesisContainsRealServiceResourcesAndNoAccountKeys() throws Exception {
    var first = AzureInfrastructure.template(config());
    assertEquals(first, AzureInfrastructure.template(config()));
    var resources = first.path("resources");
    Set<String> types = new HashSet<>(); resources.forEach(value -> types.add(value.path("type").asText()));
    assertTrue(types.containsAll(Set.of("Microsoft.App/containerApps", "Microsoft.DocumentDB/databaseAccounts",
        "Microsoft.Storage/storageAccounts", "Microsoft.EventHub/namespaces/eventhubs", "Microsoft.SignalRService/WebPubSub/hubs",
        "Microsoft.ManagedIdentity/userAssignedIdentities", "Microsoft.Authorization/roleAssignments")));
    assertFalse(first.toString().contains("listKeys"));
    assertFalse(first.toString().contains("AccountKey="));
    assertEquals("secureString", first.path("parameters").path("upstreamToken").path("type").asText());
    var file = AzureInfrastructure.write(config(), directory.resolve("synth/template.json"));
    assertEquals(first, Json.MAPPER.readTree(Files.readString(file)));
  }
  @Test void partitionTtlAndIdentitySettingsMatchProviderContracts() {
    var resources = AzureInfrastructure.template(config()).path("resources");
    for (var resource : resources) {
      String name = resource.path("name").asText();
      if (name.endsWith("/state")) {
        assertEquals("/pk", resource.at("/properties/resource/partitionKey/paths/0").asText());
        assertEquals(-1, resource.at("/properties/resource/defaultTtl").asInt());
      }
      if (name.endsWith("/leases")) assertEquals("/id", resource.at("/properties/resource/partitionKey/paths/0").asText());
      if (resource.path("type").asText().equals("Microsoft.App/containerApps")) {
        assertEquals("UserAssigned", resource.at("/identity/type").asText());
        assertEquals(1, resource.at("/properties/template/scale/minReplicas").asInt());
        assertFalse(resource.at("/properties/configuration/ingress/allowInsecure").asBoolean(true));
        assertEquals(8080, resource.at("/properties/configuration/ingress/targetPort").asInt());
      }
    }
  }
  @Test void rejectsInvalidNamesAndUnknownConfigFields() throws Exception {
    var tree = Json.MAPPER.valueToTree(config());
    ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("credential", "not-a-config-option");
    Path input = directory.resolve("azure.json"); Files.writeString(input, tree.toString());
    assertThrows(IOException.class, () -> AzureDeploymentConfig.read(input));
    assertThrows(IllegalArgumentException.class, () -> new AzureDeploymentConfig("../oops", config().subscriptionId(),
        config().resourceGroup(), config().location(), config().image(), config().applicationClass(), null, 1, 8080));
    assertThrows(IllegalArgumentException.class, () -> new AzureDeploymentConfig(config().application(), config().subscriptionId(),
        config().resourceGroup(), config().location(), config().image(), config().applicationClass(), List.of("bad--name"), 1, 8080));
  }
  @Test void callbacksRequireExactTokenRejectDuplicatesAndMalformedEncoding() {
    String token = "a".repeat(64);
    var guard = new UpstreamAuthentication(token, exchange -> fail("unused"));
    assertTrue(guard.accepts(URI.create("https://app/azure/webpubsub?code=" + token)));
    assertFalse(guard.accepts(URI.create("https://app/azure/webpubsub?code=" + token + "&code=" + token)));
    assertFalse(guard.accepts(URI.create("https://app/azure/webpubsub?code=" + "b".repeat(64))));
    assertFalse(guard.accepts(URI.create("https://app/azure/webpubsub")));
  }
  @Test void refusesExistingGroupsWithoutOwnershipBeforeMutation() throws Exception {
    AtomicInteger mutations = new AtomicInteger();
    try (var server = server(exchange -> {
      if (!exchange.getRequestMethod().equals("GET")) mutations.incrementAndGet();
      respond(exchange, 200, "{\"tags\":{\"owner\":\"someone-else\"}}");
    })) {
      var deployment = new AzureDeployment(client(server));
      assertThrows(IllegalStateException.class, () -> deployment.deploy(config(), Duration.ofSeconds(2)));
      assertThrows(IllegalStateException.class, () -> deployment.nuke(config(), config().resourceGroup(), Duration.ofSeconds(2)));
      assertEquals(0, mutations.get());
    }
  }
  @Test void deployCreatesIsolatedGroupPollsCompletionAndReturnsActualOutputs() throws Exception {
    AtomicInteger groupReads = new AtomicInteger(), mutations = new AtomicInteger();
    try (var server = server(exchange -> {
      assertEquals("Bearer contract-token", exchange.getRequestHeaders().getFirst("Authorization"));
      if (exchange.getRequestURI().getPath().contains("/deployments/")) {
        if (exchange.getRequestMethod().equals("PUT")) {
          var body = Json.MAPPER.readTree(exchange.getRequestBody());
          assertEquals("Incremental", body.at("/properties/mode").asText());
          assertTrue(body.at("/properties/template/resources").isArray());
          mutations.incrementAndGet(); respond(exchange, 201, "{\"properties\":{\"provisioningState\":\"Accepted\"}}");
        } else respond(exchange, 200, "{\"properties\":{\"provisioningState\":\"Succeeded\",\"outputs\":{\"url\":{\"value\":\"https://actual.azurecontainerapps.io\"}}}}");
      } else if (exchange.getRequestMethod().equals("GET")) { groupReads.incrementAndGet(); respond(exchange, 404, "{}"); }
      else { mutations.incrementAndGet(); respond(exchange, 201, "{}"); }
    })) {
      var outputs = new AzureDeployment(client(server)).deploy(config(), Duration.ofSeconds(2));
      assertEquals("https://actual.azurecontainerapps.io", outputs.path("url").path("value").asText());
      assertEquals(1, groupReads.get()); assertEquals(2, mutations.get());
    }
  }
  @Test void teardownRequiresMatchingNameAndConfirmsRemoteAbsence() throws Exception {
    AtomicInteger deletes = new AtomicInteger();
    try (var server = server(exchange -> {
      if (exchange.getRequestMethod().equals("DELETE")) { deletes.incrementAndGet(); respond(exchange, 202, "{}"); }
      else if (deletes.get() > 0) respond(exchange, 404, "{}");
      else respond(exchange, 200, "{\"tags\":{\"booster-java-application\":\"test-app\"}}");
    })) {
      var deployment = new AzureDeployment(client(server));
      assertThrows(IllegalArgumentException.class, () -> deployment.nuke(config(), "wrong-group", Duration.ofSeconds(2)));
      assertEquals(0, deletes.get());
      deployment.nuke(config(), config().resourceGroup(), Duration.ofSeconds(2));
      assertEquals(1, deletes.get());
    }
  }
  @Test void failedDeploymentAndForeignPollingOriginsCannotSucceed() throws Exception {
    try (var server = server(exchange -> respond(exchange, 200, "{\"properties\":{\"provisioningState\":\"Failed\",\"error\":{\"code\":\"QuotaExceeded\"}}}"))) {
      var client = client(server);
      var failure = assertThrows(IOException.class, () -> client.await("/deployment", Duration.ofSeconds(2), false));
      assertTrue(failure.getMessage().contains("QuotaExceeded"));
      assertThrows(IllegalArgumentException.class, () -> client.request("GET", "https://different.invalid/capture", null));
    }
  }
  private record Server(HttpServer http) implements AutoCloseable { public void close() { http.stop(0); } }
  private static Server server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", handler); server.start(); return new Server(server);
  }
  private static AzureArmClient client(Server server) {
    return new AzureArmClient(URI.create("http://127.0.0.1:" + server.http().getAddress().getPort()),
        context -> Mono.just(new AccessToken("contract-token", OffsetDateTime.now().plusHours(1))), HttpClient.newHttpClient());
  }
  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
    try (exchange) { byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes); }
  }
}
