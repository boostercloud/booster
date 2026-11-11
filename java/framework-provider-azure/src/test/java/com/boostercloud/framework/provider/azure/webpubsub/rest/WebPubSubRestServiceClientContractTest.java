package com.boostercloud.framework.provider.azure.webpubsub.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.support.FakeAzureServer;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.RecordedRequest;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.Response;
import com.boostercloud.framework.provider.azure.support.Fixtures;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubException;
import com.boostercloud.framework.types.BoosterJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests of the Web PubSub REST client: {@code sendToConnection} request shape and the access-key JWT
 * (decoded and verified here independently), and the retry policy.
 */
class WebPubSubRestServiceClientContractTest {

  static final String ACCESS_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg=";
  static final Instant NOW = Instant.parse("2026-11-10T09:15:04Z");

  private FakeAzureServer server;
  private final List<Long> sleeps = new ArrayList<>();

  @BeforeEach
  void start() {
    server = new FakeAzureServer();
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private WebPubSubRestServiceClient client() {
    return new WebPubSubRestServiceClient(
        WebPubSubConnectionString.parse("Endpoint=" + server.baseUrl() + ";AccessKey=" + ACCESS_KEY + ";Version=1.0;"),
        "booster", HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        Clock.fixed(NOW, ZoneOffset.UTC), sleeps::add, () -> 0.0);
  }

  @Test
  void sendsTextMessagesToTheConnectionWithAnAccessKeyJwt() throws Exception {
    server.enqueue(Response.empty(202));

    client().sendToConnection("Xy12 abCD", "{\"type\":\"connection_ack\"}", "text/plain");

    RecordedRequest request = server.request(0);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.rawPath()).isEqualTo("/api/hubs/booster/connections/Xy12%20abCD/:send");
    assertThat(request.query()).isEqualTo("api-version=2021-10-01");
    assertThat(request.header("content-type")).isEqualTo("text/plain");
    assertThat(request.body()).isEqualTo("{\"type\":\"connection_ack\"}");

    String authorization = request.header("authorization");
    assertThat(authorization).startsWith("Bearer ");
    String[] jwt = authorization.substring("Bearer ".length()).split("\\.");
    assertThat(jwt).hasSize(3);
    Base64.Decoder decoder = Base64.getUrlDecoder();
    assertThat(BoosterJson.parse(new String(decoder.decode(jwt[0]), StandardCharsets.UTF_8)))
        .isEqualTo(Map.of("alg", "HS256", "typ", "JWT"));
    assertThat(BoosterJson.parse(new String(decoder.decode(jwt[1]), StandardCharsets.UTF_8))).isEqualTo(Map.of(
        "iat", (int) NOW.getEpochSecond(),
        "exp", (int) NOW.getEpochSecond() + 3600,
        // new URL(request.url) with port = '': the query string stays, the port goes
        "aud", "http://" + URI.create(server.baseUrl()).getHost()
            + "/api/hubs/booster/connections/Xy12%20abCD/:send?api-version=2021-10-01"));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(ACCESS_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expectedSignature = mac.doFinal((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.UTF_8));
    assertThat(decoder.decode(jwt[2])).isEqualTo(expectedSignature);
  }

  @Test
  void retriesThrottlingWithRetryAfterAndServerErrorsExponentially() {
    server.enqueue(Response.empty(429).withHeader("Retry-After", "2"))
        .enqueue(Response.empty(500))
        .enqueue(Response.empty(202));

    client().sendToConnection("c", "m", "text/plain");

    assertThat(sleeps).containsExactly(2000L, 1000L);
    assertThat(server.requests()).hasSize(3);
  }

  @Test
  void clientErrorsAreThrownWithoutRetry() {
    server.enqueue(Fixtures.response("webpubsub/error-404.json", 404));

    assertThatThrownBy(() -> client().sendToConnection("gone", "m", "text/plain"))
        .isInstanceOfSatisfying(WebPubSubException.class, error -> {
          assertThat(error.statusCode()).isEqualTo(404);
          assertThat(error.getMessage()).contains("The connection does not exist.");
        });
    assertThat(sleeps).isEmpty();
  }

  @Test
  void givesUpAfterThreeRetries() {
    for (int i = 0; i < 4; i++) {
      server.enqueue(Response.empty(503));
    }

    assertThatThrownBy(() -> client().sendToConnection("c", "m", "text/plain"))
        .isInstanceOfSatisfying(WebPubSubException.class, error -> assertThat(error.statusCode()).isEqualTo(503));
    assertThat(server.requests()).hasSize(4);
    assertThat(sleeps).hasSize(3);
  }

  @Test
  void theAudienceIsTheRequestUrlWithItsQueryAndWithoutPort() {
    assertThat(WebPubSubTokens.audience(URI.create(
        "https://My-WPS.webpubsub.azure.com:8080/api/hubs/booster/connections/a%2Fb/:send?api-version=2021-10-01")))
        .isEqualTo("https://my-wps.webpubsub.azure.com/api/hubs/booster/connections/a%2Fb/:send?api-version=2021-10-01");
    assertThat(WebPubSubTokens.audience(URI.create("https://wps.webpubsub.azure.com")))
        .isEqualTo("https://wps.webpubsub.azure.com/");
  }

  @Test
  void parsesConnectionStringsWithAPort() {
    WebPubSubConnectionString parsed =
        WebPubSubConnectionString.parse("Endpoint=https://wps.webpubsub.azure.com;AccessKey=k;Port=8080;Version=1.0;");

    assertThat(parsed.endpoint().toString()).isEqualTo("https://wps.webpubsub.azure.com:8080");
    assertThat(parsed.toString()).doesNotContain("AccessKey=k");
    assertThatThrownBy(() -> WebPubSubConnectionString.parse(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
