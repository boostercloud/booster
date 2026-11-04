package io.boostercloud.booster.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.boostercloud.booster.core.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the packaged executable in separate JVMs, including durable process restart. */
class CliSmokeIT {
  @TempDir Path temporary;

  private record ServerProcess(Process process, int port) implements AutoCloseable {
    @Override public void close() throws Exception {
      process.destroy();
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Server process did not terminate");
      }
    }
  }

  private ServerProcess start(String logName) throws Exception {
    Path jar = Path.of("target", "booster-java-" + BoosterCli.VERSION + "-all.jar").toAbsolutePath();
    assertTrue(Files.isRegularFile(jar), "Run mvn verify so package creates the executable JAR first");
    Path log = temporary.resolve(logName);
    Process process = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(),
        "demo", "--port", "0", "--data", temporary.resolve("data").toString())
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      Pattern port = Pattern.compile("started on 127\\.0\\.0\\.1:(\\d+)");
      while (System.nanoTime() < deadline) {
        String output = Files.readString(log);
        var match = port.matcher(output);
        if (match.find()) return new ServerProcess(process, Integer.parseInt(match.group(1)));
        if (!process.isAlive()) fail("CLI exited during startup:\n" + output);
        Thread.sleep(50);
      }
      fail("CLI startup timed out:\n" + Files.readString(log));
      throw new AssertionError();
    } catch (Exception | AssertionError failure) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw failure;
    }
  }

  private com.fasterxml.jackson.databind.JsonNode graphql(HttpClient client, int port, String query) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/graphql"))
        .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(Map.of("query", query)))).build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    var body = Json.MAPPER.readTree(response.body());
    assertFalse(body.has("errors"), response.body());
    return body.path("data");
  }

  @Test @Timeout(60)
  void executableServesGraphqlAndRestoresTheJournalAfterProcessRestart() throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      try (var server = start("first.log")) {
        var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/health"))
            .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        graphql(client, server.port(), "mutation { OpenCart(input: {cartId: \"process-cart\"}) { id } }");
        graphql(client, server.port(), "mutation { AddItem(input: {cartId: \"process-cart\", sku: \"book\", quantity: 2, unitPriceCents: 1250}) { id } }");
        var cart = graphql(client, server.port(), "{ CartView(id: \"process-cart\") { id totalQuantity totalCents checkedOut } }").path("CartView");
        assertEquals(2500, cart.path("totalCents").asLong());
        assertEquals(2, cart.path("totalQuantity").asInt());
      }
      try (var server = start("second.log")) {
        var cart = graphql(client, server.port(), "{ CartView(id: \"process-cart\") { totalCents checkedOut } }").path("CartView");
        assertEquals(2500, cart.path("totalCents").asLong());
        graphql(client, server.port(), "mutation { CheckoutCart(input: {cartId: \"process-cart\"}) { id } }");
        assertTrue(graphql(client, server.port(), "{ CartView(id: \"process-cart\") { checkedOut } }")
            .path("CartView").path("checkedOut").asBoolean());
      }
    }
  }
}
