package com.boostercloud.framework.provider.azure.infrastructure.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The production transport against an in-JVM HTTP server. */
class JdkHttpTransportTest {

  @TempDir Path directory;

  private HttpServer server;
  private final AtomicReference<String> receivedBody = new AtomicReference<>();
  private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
  private final AtomicReference<String> receivedMethod = new AtomicReference<>();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      receivedMethod.set(exchange.getRequestMethod());
      receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("X-Custom-Header", "value");
      exchange.sendResponseHeaders(201, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void streams_file_bodies_and_returns_the_response() throws IOException {
    Path file = directory.resolve("package.zip");
    Files.writeString(file, "zip bytes");
    URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/zipDeploy?isAsync=true");
    HttpTransport.Response response = new JdkHttpTransport().send(
        new HttpTransport.Request("POST", uri, Map.of("Authorization", "Basic abc"), file, Duration.ofSeconds(5)));
    assertThat(response.status()).isEqualTo(201);
    assertThat(response.bodyText()).isEqualTo("{\"ok\":true}");
    assertThat(response.header("x-custom-header")).contains("value");
    assertThat(receivedMethod.get()).isEqualTo("POST");
    assertThat(receivedAuthorization.get()).isEqualTo("Basic abc");
    assertThat(receivedBody.get()).isEqualTo("zip bytes");
  }

  @Test
  void sends_byte_bodies_and_empty_requests() {
    URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    new JdkHttpTransport().send(new HttpTransport.Request("PUT", uri, Map.of(), "{}".getBytes(StandardCharsets.UTF_8),
        null));
    assertThat(receivedBody.get()).isEqualTo("{}");
    new JdkHttpTransport().send(new HttpTransport.Request("DELETE", uri, Map.of(), null, null));
    assertThat(receivedMethod.get()).isEqualTo("DELETE");
  }

  @Test
  void reports_transport_failures() {
    int port = server.getAddress().getPort();
    server.stop(0);
    URI uri = URI.create("http://127.0.0.1:" + port + "/");
    assertThatThrownBy(() -> new JdkHttpTransport().send(new HttpTransport.Request("GET", uri, Map.of(), null,
        Duration.ofSeconds(2)))).isInstanceOf(UncheckedIOException.class).hasMessageStartingWith("GET " + uri);
  }

  @Test
  void requests_reject_unknown_body_types() {
    assertThatThrownBy(() -> new HttpTransport.Request("GET", URI.create("http://x"), Map.of(), "text", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
