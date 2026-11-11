package com.boostercloud.framework.provider.azure.infrastructure.azure;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.provider.azure.http.UriEncoding;
import com.boostercloud.framework.provider.azure.infrastructure.helper.JsonText;
import com.boostercloud.framework.types.BoosterJson;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A minimal Azure Resource Manager client: bearer authentication with the {@code https://management.azure.com/.default}
 * scope, JSON bodies, ARM error mapping ({@link ArmError}) and the long-running operation protocol the SDK pollers
 * ({@code begin...AndWait}, {@code pollUntilDone}) implement:
 *
 * <ul>
 *   <li>{@code Azure-AsyncOperation}: poll that URL until its {@code status} is {@code Succeeded}, {@code Failed} or
 *       {@code Canceled}; a {@code PUT}/{@code PATCH} then reads the resource, a {@code POST} reads the
 *       {@code Location} when there is one;
 *   <li>{@code Location} only: poll it while it answers {@code 202}; the final response is the result;
 *   <li>neither: the initial response is the result, except that a {@code PUT} whose
 *       {@code properties.provisioningState} is not terminal is polled with {@code GET} until it is.
 * </ul>
 *
 * The polling interval is the {@code Retry-After} header (seconds) or {@value #DEFAULT_POLL_INTERVAL_MS} ms.
 */
public final class ArmClient {

  /** The Resource Manager endpoint. */
  public static final String ENDPOINT = "https://management.azure.com";

  /** The token scope of Resource Manager calls. */
  public static final String SCOPE = ENDPOINT + "/.default";

  /** The default polling interval of long-running operations (the SDK's 2 s). */
  public static final long DEFAULT_POLL_INTERVAL_MS = 2000;

  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
  private static final Set<String> TERMINAL_STATES = Set.of("succeeded", "failed", "canceled", "cancelled");

  private final TokenCredential credential;
  private final String subscriptionId;
  private final HttpTransport transport;
  private final Retrier.Sleeper sleeper;

  /**
   * @param credential the credential
   * @param subscriptionId the subscription of every request
   * @param transport the HTTP transport
   * @param sleeper waits between polls
   */
  public ArmClient(TokenCredential credential, String subscriptionId, HttpTransport transport, Retrier.Sleeper sleeper) {
    this.credential = Objects.requireNonNull(credential, "credential");
    this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
    this.transport = Objects.requireNonNull(transport, "transport");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
  }

  /**
   * @param path the path below {@code /subscriptions/<id>}, with encoded segments, e.g.
   *     {@code /resourcegroups/myrg}
   * @param apiVersion the API version
   * @return the absolute URL
   */
  public URI url(String path, String apiVersion) {
    return URI.create(ENDPOINT + "/subscriptions/" + UriEncoding.encodeURIComponent(subscriptionId) + path
        + "?api-version=" + apiVersion);
  }

  /**
   * @param segment a path segment (resource group, resource name...)
   * @return the segment percent-encoded
   */
  public static String segment(String segment) {
    return UriEncoding.encodeURIComponent(segment);
  }

  /**
   * A plain request.
   *
   * @param method the verb
   * @param path the path below the subscription
   * @param apiVersion the API version
   * @param body a raw JSON body, or {@code null}
   * @return the parsed response body ({@code {}} when empty)
   * @throws ArmError when the status is 400 or more
   */
  public Map<String, Object> request(String method, String path, String apiVersion, Object body) {
    HttpTransport.Response response = send(method, url(path, apiVersion), body);
    checkStatus(response);
    return json(response);
  }

  /**
   * A long-running operation, waiting for its end ({@code begin<Operation>AndWait} / {@code pollUntilDone}).
   *
   * @param method the verb
   * @param path the path below the subscription
   * @param apiVersion the API version
   * @param body a raw JSON body, or {@code null}
   * @return the final result body ({@code {}} when there is none)
   * @throws ArmError when a request fails or the operation ends in {@code Failed}/{@code Canceled}
   */
  public Map<String, Object> beginAndWait(String method, String path, String apiVersion, Object body) {
    URI resourceUrl = url(path, apiVersion);
    HttpTransport.Response initial = send(method, resourceUrl, body);
    checkStatus(initial);
    Optional<String> asyncOperation = initial.header("Azure-AsyncOperation");
    Optional<String> location = initial.header("Location");
    if (asyncOperation.isPresent()) {
      HttpTransport.Response last = initial;
      while (true) {
        sleeper.sleep(pollInterval(last));
        last = send("GET", URI.create(asyncOperation.get()), null);
        checkStatus(last);
        Map<String, Object> operation = json(last);
        String status = String.valueOf(operation.getOrDefault("status", "")).toLowerCase(Locale.ROOT);
        if (TERMINAL_STATES.contains(status)) {
          if (!"succeeded".equals(status)) {
            throw operationFailed(operation);
          }
          break;
        }
      }
      return switch (method) {
        case "PUT", "PATCH" -> request("GET", resourceUrl);
        case "POST" -> location.isPresent() ? request("GET", URI.create(location.get())) : Map.of();
        default -> Map.of();
      };
    }
    if (location.isPresent() && initial.status() == 202) {
      HttpTransport.Response last = initial;
      while (true) {
        sleeper.sleep(pollInterval(last));
        last = send("GET", URI.create(location.get()), null);
        checkStatus(last);
        if (last.status() != 202) {
          return json(last);
        }
      }
    }
    Map<String, Object> result = json(initial);
    if ("PUT".equals(method)) {
      HttpTransport.Response last = initial;
      while (!isTerminal(provisioningState(result))) {
        sleeper.sleep(pollInterval(last));
        last = send("GET", resourceUrl, null);
        checkStatus(last);
        result = json(last);
      }
      if ("failed".equals(provisioningState(result)) || "canceled".equals(provisioningState(result))) {
        throw operationFailed(result);
      }
    }
    return result;
  }

  private Map<String, Object> request(String method, URI url) {
    HttpTransport.Response response = send(method, url, null);
    checkStatus(response);
    return json(response);
  }

  private HttpTransport.Response send(String method, URI url, Object body) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + credential.getToken(SCOPE));
    headers.put("Accept", "application/json");
    byte[] bytes = null;
    if (body != null) {
      headers.put("Content-Type", "application/json");
      bytes = JsonText.stringify(body).getBytes(StandardCharsets.UTF_8);
    }
    return transport.send(new HttpTransport.Request(method, url, headers, bytes, REQUEST_TIMEOUT));
  }

  private static void checkStatus(HttpTransport.Response response) {
    if (response.status() < 400) {
      return;
    }
    Map<String, Object> body = json(response);
    Object error = body.get("error") instanceof Map<?, ?> nested ? nested : body;
    String code = error instanceof Map<?, ?> map && map.get("code") instanceof String text ? text : null;
    String message = error instanceof Map<?, ?> map && map.get("message") instanceof String text
        ? text
        : "Unexpected status code: " + response.status();
    throw new ArmError(response.status(), code, message);
  }

  private static ArmError operationFailed(Map<String, Object> operation) {
    Object error = operation.get("error");
    if (error == null && operation.get("properties") instanceof Map<?, ?> properties) {
      error = properties.get("error");
    }
    String code = error instanceof Map<?, ?> map && map.get("code") instanceof String text ? text : null;
    String message = error instanceof Map<?, ?> map && map.get("message") instanceof String text
        ? text
        : "The long-running operation has failed";
    return new ArmError(0, code, message);
  }

  private static String provisioningState(Map<String, Object> resource) {
    if (resource.get("properties") instanceof Map<?, ?> properties
        && properties.get("provisioningState") instanceof String state) {
      return state.toLowerCase(Locale.ROOT);
    }
    return "succeeded";
  }

  private static boolean isTerminal(String state) {
    return TERMINAL_STATES.contains(state);
  }

  private static long pollInterval(HttpTransport.Response response) {
    Optional<String> retryAfter = response.header("Retry-After");
    if (retryAfter.isPresent()) {
      try {
        return Long.parseLong(retryAfter.get().trim()) * 1000;
      } catch (NumberFormatException ignored) {
        // An HTTP date: use the default interval
      }
    }
    return DEFAULT_POLL_INTERVAL_MS;
  }

  private static Map<String, Object> json(HttpTransport.Response response) {
    String text = response.bodyText();
    if (text.isBlank()) {
      return Map.of();
    }
    try {
      return BoosterJson.parseObject(text);
    } catch (RuntimeException e) {
      return Map.of();
    }
  }
}
