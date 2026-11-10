package com.boostercloud.framework.provider.azure.webpubsub.rest;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.http.UriEncoding;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubException;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClient;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClientFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.function.DoubleSupplier;

/**
 * Production {@link WebPubSubServiceClient}: the Web PubSub data-plane REST API
 * ({@code POST <endpoint>/api/hubs/<hub>/connections/<connectionId>/:send?api-version=...}) authenticated with the
 * access-key JWT of {@link WebPubSubTokens}.
 *
 * <p>Retries follow {@code @azure/core-rest-pipeline}'s default policy: at most 3 retries; a {@code Retry-After}
 * header (429/503) is honoured; connection failures, 408 and 5xx (except 501 and 505) wait
 * {@code d/2 + random(0, d/2)} with {@code d = min(64000, 1000 * 2^retry)} milliseconds.
 */
public final class WebPubSubRestServiceClient implements WebPubSubServiceClient {

  /** Data-plane API version. */
  public static final String API_VERSION = "2021-10-01";
  /** Maximum retries of the default pipeline policy. */
  public static final int MAX_RETRIES = 3;
  static final long RETRY_DELAY_IN_MS = 1000;
  static final long MAX_RETRY_DELAY_IN_MS = 64_000;

  private final WebPubSubConnectionString connectionString;
  private final String hub;
  private final HttpClient httpClient;
  private final Clock clock;
  private final Retrier.Sleeper sleeper;
  private final DoubleSupplier random;

  /**
   * @param connectionString the connection string
   * @param hub the hub
   * @param httpClient the HTTP client
   * @param clock clock used for the token timestamps
   * @param sleeper waits between retries
   * @param random jitter source in [0, 1)
   */
  public WebPubSubRestServiceClient(
      WebPubSubConnectionString connectionString,
      String hub,
      HttpClient httpClient,
      Clock clock,
      Retrier.Sleeper sleeper,
      DoubleSupplier random) {
    this.connectionString = connectionString;
    this.hub = hub;
    this.httpClient = httpClient;
    this.clock = clock;
    this.sleeper = sleeper;
    this.random = random;
  }

  private static final HttpClient SHARED_HTTP_CLIENT =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  /**
   * @return the production factory ({@code new WebPubSubServiceClient(connectionString, hub)}), sharing one HTTP
   *     client between the clients it creates
   */
  public static WebPubSubServiceClientFactory factory() {
    return (connectionString, hub) -> new WebPubSubRestServiceClient(
        WebPubSubConnectionString.parse(connectionString), hub, SHARED_HTTP_CLIENT, Clock.systemUTC(),
        Retrier.Sleeper.THREAD_SLEEP, Math::random);
  }

  @Override
  public void sendToConnection(String connectionId, String message, String contentType) {
    String path = connectionString.endpoint() + "/api/hubs/" + UriEncoding.encodeURIComponent(hub) + "/connections/"
        + UriEncoding.encodeURIComponent(connectionId) + "/:send";
    URI target = URI.create(path + "?api-version=" + API_VERSION);
    for (int retry = 0; ; retry++) {
      long now = clock.instant().getEpochSecond();
      HttpRequest request = HttpRequest.newBuilder(target)
          .timeout(Duration.ofSeconds(60))
          .header("Authorization", "Bearer " + WebPubSubTokens.accessToken(path, connectionString.accessKey(), now))
          .header("Content-Type", contentType)
          .POST(HttpRequest.BodyPublishers.ofString(message == null ? "" : message, StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response;
      try {
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (IOException e) {
        if (retry >= MAX_RETRIES) {
          throw new WebPubSubException("Could not send the message to Web PubSub: " + e.getMessage(), 0, e);
        }
        sleeper.sleep(exponentialDelay(retry));
        continue;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new WebPubSubException("Interrupted while calling Web PubSub", 0, e);
      }
      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        return;
      }
      Long retryAfter = retryAfterMillis(response);
      boolean throttled = (status == 429 || status == 503) && retryAfter != null;
      boolean transientStatus = status == 408 || (status >= 500 && status != 501 && status != 505);
      if (retry >= MAX_RETRIES || !(throttled || transientStatus)) {
        String detail = response.body() == null || response.body().isBlank() ? "" : ": " + response.body();
        throw new WebPubSubException("Web PubSub rejected the request with status " + status + detail, status, null);
      }
      sleeper.sleep(throttled ? retryAfter : exponentialDelay(retry));
    }
  }

  long exponentialDelay(int retry) {
    double exponential = RETRY_DELAY_IN_MS * Math.pow(2, retry);
    double clamped = Math.min(MAX_RETRY_DELAY_IN_MS, exponential);
    return (long) (clamped / 2 + Math.floor(random.getAsDouble() * (clamped / 2 + 1)));
  }

  private static Long retryAfterMillis(HttpResponse<?> response) {
    String millis = response.headers().firstValue("retry-after-ms")
        .or(() -> response.headers().firstValue("x-ms-retry-after-ms")).orElse(null);
    try {
      if (millis != null) {
        return (long) Double.parseDouble(millis);
      }
      String seconds = response.headers().firstValue("retry-after").orElse(null);
      return seconds == null ? null : (long) (Double.parseDouble(seconds) * 1000);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
