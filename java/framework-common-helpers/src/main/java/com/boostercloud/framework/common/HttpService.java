package com.boostercloud.framework.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Port of {@code http-service.ts}: a minimal GET/POST client (used by the health indicators) on
 * {@link java.net.http.HttpClient}.
 *
 * <p>Same rules as TypeScript: {@code Content-Type} is always sent (default {@code application/json}), the body is
 * sent as given, redirects are not followed, a status outside 200-299 fails unless it is in
 * {@code acceptedStatusCodes}, and the response body is returned as an undecoded UTF-8 string. Unlike Node,
 * {@code Content-Length} is computed by the client from the UTF-8 bytes (TypeScript uses the string length), and the
 * timeout is the time to receive the response headers (Node: socket inactivity).
 */
public final class HttpService {

  /** Default {@code Content-Type}. */
  public static final String DEFAULT_CONTENT_TYPE = "application/json";

  /** Default timeout in milliseconds. */
  public static final int DEFAULT_TIMEOUT = 10000;

  private static final HttpClient CLIENT =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private HttpService() {}

  /** HTTP methods supported by {@link #request}. */
  public enum Method {
    /** {@code GET}. */
    GET,
    /** {@code POST}. */
    POST
  }

  /**
   * Request options. Port of {@code PostConfiguration}; every field is optional.
   *
   * @param contentType the {@code Content-Type} (default {@code application/json})
   * @param timeout timeout in milliseconds (default 10000)
   * @param acceptedStatusCodes additional status codes accepted as success (default none)
   */
  public record PostConfiguration(String contentType, Integer timeout, List<Integer> acceptedStatusCodes) {
    /**
     * @return the defaults
     */
    public static PostConfiguration defaults() {
      return new PostConfiguration(null, null, null);
    }

    /**
     * @param statusCodes additional accepted status codes
     * @return options accepting them
     */
    public static PostConfiguration accepting(Integer... statusCodes) {
      return new PostConfiguration(null, null, List.of(statusCodes));
    }
  }

  /**
   * Result of a request. Port of {@code PostResult}.
   *
   * @param status the status code
   * @param body the response body, as a string
   */
  public record PostResult(int status, String body) {}

  /**
   * @param url the URL
   * @return the result of a {@code GET}
   */
  public static PostResult request(String url) {
    return request(url, Method.GET, "", PostConfiguration.defaults());
  }

  /**
   * @param url the URL
   * @param method the method
   * @param data the body ({@code ""} for none)
   * @return the result
   */
  public static PostResult request(String url, Method method, String data) {
    return request(url, method, data, PostConfiguration.defaults());
  }

  /**
   * {@code request(url, method = 'GET', data = '', config = {})}.
   *
   * @param url the URL ({@code http} or {@code https})
   * @param method the method
   * @param data the body ({@code ""} or {@code null} for none)
   * @param config the options
   * @return the status and body
   * @throws HttpServiceError {@code HTTP status code <n>} for a rejected status, {@code Request time out} on timeout,
   *     or the connection failure (as cause)
   */
  public static PostResult request(String url, Method method, String data, PostConfiguration config) {
    return request(CLIENT, url, method, data, config);
  }

  static PostResult request(HttpClient client, String url, Method method, String data, PostConfiguration config) {
    Objects.requireNonNull(url, "url");
    PostConfiguration options = config == null ? PostConfiguration.defaults() : config;
    String contentType = options.contentType() == null ? DEFAULT_CONTENT_TYPE : options.contentType();
    int timeout = options.timeout() == null ? DEFAULT_TIMEOUT : options.timeout();
    List<Integer> accepted = options.acceptedStatusCodes() == null ? List.of() : options.acceptedStatusCodes();
    String body = data == null ? "" : data;
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMillis(timeout))
        .header("Content-Type", contentType)
        .method((method == null ? Method.GET : method).name(),
            body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (HttpTimeoutException e) {
      throw new HttpServiceError("Request time out", e);
    } catch (IOException e) {
      throw new HttpServiceError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpServiceError("Request interrupted", e);
    }
    int status = response.statusCode();
    if (status <= 0) {
      throw new HttpServiceError("Unknown HTTP status code", null);
    }
    if ((status < 200 || status > 299) && !accepted.contains(status)) {
      throw new HttpServiceError("HTTP status code " + status, null);
    }
    return new PostResult(status, response.body());
  }
}
