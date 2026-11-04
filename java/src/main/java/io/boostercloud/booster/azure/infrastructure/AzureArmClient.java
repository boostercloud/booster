package io.boostercloud.booster.azure.infrastructure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;

/** Small ARM client with bounded retries and asynchronous-operation polling. */
public final class AzureArmClient {
  public record Response(int status, JsonNode body, String asynchronousOperation, String location) {}
  private final URI endpoint;
  private final TokenCredential credential;
  private final HttpClient http;
  public AzureArmClient(TokenCredential credential) {
    this(URI.create("https://management.azure.com"), credential, defaultHttpClient());
  }
  /** Endpoint/client injection supports isolated HTTP contract tests and sovereign clouds. */
  public AzureArmClient(URI endpoint, TokenCredential credential, HttpClient http) {
    this.endpoint = Objects.requireNonNull(endpoint);
    this.credential = Objects.requireNonNull(credential);
    this.http = Objects.requireNonNull(http);
    if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
        || (!endpoint.getScheme().equals("https") && !(endpoint.getScheme().equals("http") &&
            (endpoint.getHost().equals("127.0.0.1") || endpoint.getHost().equals("localhost")))))
      throw new IllegalArgumentException("ARM endpoint must use HTTPS (HTTP allowed only for loopback contract tests)");
  }
  public static HttpClient defaultHttpClient() {
    var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER);
    String proxy = System.getenv("HTTPS_PROXY");
    if (proxy == null) proxy = System.getenv("https_proxy");
    if (proxy != null && !proxy.isBlank()) {
      URI uri = URI.create(proxy);
      builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort())));
    }
    return builder.build();
  }
  public Response request(String method, String path, JsonNode body) throws IOException, InterruptedException {
    URI uri = endpoint.resolve(path);
    if (!Objects.equals(uri.getScheme(), endpoint.getScheme()) || !Objects.equals(uri.getAuthority(), endpoint.getAuthority()))
      throw new IllegalArgumentException("ARM operation URL must have the configured origin");
    for (int attempt = 0; attempt < 5; attempt++) {
      var token = credential.getToken(new TokenRequestContext().addScopes(endpoint.getScheme() + "://" + endpoint.getAuthority() + "/.default"))
          .block(Duration.ofSeconds(30));
      if (token == null) throw new IllegalStateException("Azure Identity returned no token");
      var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
          .header("Authorization", "Bearer " + token.getToken()).header("Accept", "application/json");
      builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofByteArray(Json.MAPPER.writeValueAsBytes(body)));
      if (body != null) builder.header("Content-Type", "application/json");
      var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var stream = response.body()) { bytes = stream.readNBytes(8_388_609); }
      if (bytes.length > 8_388_608) throw new IOException("ARM response exceeds 8 MiB");
      int status = response.statusCode();
      if ((status == 429 || status == 502 || status == 503 || status == 504) && attempt < 4) {
        long seconds = Math.min(30, 1L << attempt);
        try { seconds = Math.clamp(Long.parseLong(response.headers().firstValue("Retry-After").orElse("")), 1, 30); }
        catch (NumberFormatException ignored) { }
        Thread.sleep(Duration.ofSeconds(seconds)); continue;
      }
      JsonNode parsed = bytes.length == 0 ? Json.MAPPER.createObjectNode() : Json.MAPPER.readTree(bytes);
      return new Response(status, parsed, response.headers().firstValue("Azure-AsyncOperation").orElse(null),
          response.headers().firstValue("Location").orElse(null));
    }
    throw new IOException("Azure operation exhausted retries");
  }
  public static JsonNode requireSuccess(Response response) throws IOException {
    if (response.status() < 200 || response.status() >= 300)
      throw new IOException("Azure ARM returned HTTP " + response.status() + " (" + response.body().path("error").path("code").asText("unknown error") + ")");
    return response.body();
  }
  public JsonNode await(String resourcePath, Duration timeout, boolean deletion) throws IOException, InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      Response response = request("GET", resourcePath, null);
      if (deletion && response.status() == 404) return response.body();
      JsonNode value = requireSuccess(response);
      String state = value.path("properties").path("provisioningState").asText(value.path("status").asText());
      if (!deletion && state.equalsIgnoreCase("Succeeded")) return value;
      if (state.equalsIgnoreCase("Failed") || state.equalsIgnoreCase("Canceled") || state.equalsIgnoreCase("Cancelled"))
        throw new IOException("Azure operation ended in " + state + " (" + value.path("properties").path("error").path("code").asText("no error code") + ")");
      Thread.sleep(Duration.ofSeconds(2));
    }
    throw new IOException("Azure operation did not complete within " + timeout.toMinutes() + " minutes; inspect it before retrying");
  }
}
