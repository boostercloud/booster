package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sends signed requests to the Cosmos DB REST API: adds {@code x-ms-date}, {@code x-ms-version} and the master-key
 * {@code authorization} header, retries throttled (429) requests with {@link ResourceThrottleRetryPolicy} and
 * transient failures of reads and queries as the SDK does ({@link #send}), and turns error statuses into
 * {@link CosmosException}s.
 */
final class CosmosRestTransport {

  /** REST API version sent in {@code x-ms-version} (the one the JavaScript SDK v4 uses). */
  static final String API_VERSION = "2020-07-15";

  /** Request timeout (the JavaScript SDK default). */
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

  private static final String USER_AGENT = "booster-java-cosmos-rest/1.0";

  private final URI endpoint;
  private final String masterKey;
  private final CosmosRetryOptions retryOptions;
  private final HttpClient httpClient;
  private final Clock clock;
  private final Retrier.Sleeper sleeper;

  CosmosRestTransport(
      CosmosConnectionString connectionString,
      CosmosRetryOptions retryOptions,
      HttpClient httpClient,
      Clock clock,
      Retrier.Sleeper sleeper) {
    String base = connectionString.endpoint().toString();
    this.endpoint = URI.create(base.endsWith("/") ? base : base + "/");
    this.masterKey = connectionString.accountKey();
    this.retryOptions = retryOptions;
    this.httpClient = httpClient;
    this.clock = clock;
    this.sleeper = sleeper;
  }

  /**
   * A request.
   *
   * @param method HTTP verb
   * @param resourceType resource type used to sign
   * @param resourceLink resource link used to sign
   * @param path URL path relative to the endpoint (already percent-encoded)
   * @param headers extra headers
   * @param body request body, or {@code null}
   */
  record Request(
      String method, String resourceType, String resourceLink, String path, Map<String, String> headers, String body) {}

  /**
   * A response.
   *
   * @param status HTTP status
   * @param headers headers, lower-case names, first value
   * @param body raw body
   */
  record Response(int status, Map<String, String> headers, String body) {

    Object json() {
      return body == null || body.isBlank() ? null : BoosterJson.parse(body);
    }

    String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }
  }

  /**
   * Sends the request with the SDK's retry policies:
   *
   * <ul>
   *   <li>429: {@link ResourceThrottleRetryPolicy} (every request);
   *   <li>connection failures and request timeouts of reads and queries: up to {@value #MAX_CONNECTION_RETRIES}
   *       retries {@value #CONNECTION_RETRY_DELAY_MS} ms apart ({@code DefaultRetryPolicy}, which retries the
   *       connection error codes for {@code Read} and {@code Query} operations only);
   *   <li>503 and 408 answers to reads and queries: one immediate retry ({@code TimeoutFailoverRetryPolicy} with a
   *       single region: {@code maxServiceUnavailableRetryCount = 1}, no delay, never for single-region writes).
   * </ul>
   *
   * Writes (create, upsert, replace, delete, batch) are retried only when throttled, as the SDK does on a
   * single-write-region account.
   *
   * @param request the request
   * @param acceptedErrorStatuses error statuses returned instead of thrown (404 for reads)
   * @return the response
   * @throws CosmosException for other error statuses
   * @throws UncheckedIOException when the request cannot be sent
   */
  Response send(Request request, Set<Integer> acceptedErrorStatuses) {
    ResourceThrottleRetryPolicy throttling = new ResourceThrottleRetryPolicy(retryOptions);
    boolean readOrQuery = isReadOrQuery(request);
    int connectionRetries = 0;
    int unavailableRetries = 0;
    while (true) {
      Response response;
      try {
        response = sendOnce(request);
      } catch (UncheckedIOException e) {
        if (!readOrQuery || connectionRetries >= MAX_CONNECTION_RETRIES) {
          throw e;
        }
        connectionRetries++;
        sleeper.sleep(CONNECTION_RETRY_DELAY_MS);
        continue;
      }
      if (response.status() < 400 || acceptedErrorStatuses.contains(response.status())) {
        return response;
      }
      CosmosException error = toException(response);
      if (throttling.shouldRetry(error)) {
        sleeper.sleep(throttling.retryAfterInMs());
        continue;
      }
      if (readOrQuery && (error.code() == 503 || error.code() == 408)
          && unavailableRetries < MAX_SERVICE_UNAVAILABLE_RETRIES) {
        unavailableRetries++;
        continue;
      }
      throw error;
    }
  }

  /** {@code DefaultRetryPolicy.maxTries}. */
  static final int MAX_CONNECTION_RETRIES = 10;
  /** {@code DefaultRetryPolicy.retryAfterInMs}. */
  static final long CONNECTION_RETRY_DELAY_MS = 1000;
  /** {@code TimeoutFailoverRetryPolicy.maxServiceUnavailableRetryCount}. */
  static final int MAX_SERVICE_UNAVAILABLE_RETRIES = 1;

  /**
   * @param request a request
   * @return whether it is a {@code Read} or {@code Query} operation for the SDK's retry policies: a {@code GET}, a
   *     query or a query plan request
   */
  static boolean isReadOrQuery(Request request) {
    return request.method().equals("GET") || "True".equals(request.headers().get("x-ms-documentdb-isquery"));
  }

  private Response sendOnce(Request request) {
    String date = CosmosAuthorization.formatDate(clock.instant());
    HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.resolve(request.path()))
        .timeout(REQUEST_TIMEOUT)
        .header("x-ms-date", date)
        .header("x-ms-version", API_VERSION)
        .header("authorization", CosmosAuthorization.authorizationHeader(
            request.method(), request.resourceType(), request.resourceLink(), date, masterKey))
        .header("Accept", "application/json")
        .header("User-Agent", USER_AGENT);
    request.headers().forEach(builder::header);
    HttpRequest.BodyPublisher publisher = request.body() == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
    builder.method(request.method(), publisher);
    try {
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      Map<String, String> headers = new LinkedHashMap<>();
      response.headers().map().forEach((name, values) -> {
        if (!values.isEmpty()) {
          headers.put(name.toLowerCase(Locale.ROOT), values.getFirst());
        }
      });
      return new Response(response.statusCode(), headers, response.body());
    } catch (IOException e) {
      throw new UncheckedIOException("Cosmos DB request " + request.method() + " " + request.path() + " failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling Cosmos DB", e);
    }
  }

  static CosmosException toException(Response response) {
    String message = null;
    try {
      Object json = response.json();
      if (json instanceof Map<?, ?> map && map.get("message") != null) {
        message = map.get("message").toString();
      }
    } catch (RuntimeException ignored) {
      // Not JSON: use the raw body
    }
    if (message == null) {
      message = response.body() == null || response.body().isBlank()
          ? "Cosmos DB request failed with status " + response.status()
          : response.body();
    }
    return new CosmosException(message, response.status(), parseInteger(response.header("x-ms-substatus")),
        parseLong(response.header("x-ms-retry-after-ms")), response.headers(), response.body());
  }

  static Integer parseInteger(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value.strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return (long) Double.parseDouble(value.strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Map<String, String> headers(String... keyValues) {
    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      if (keyValues[i + 1] != null) {
        headers.put(keyValues[i], keyValues[i + 1]);
      }
    }
    return headers;
  }

  static List<String> list(String... values) {
    return List.of(values);
  }
}
