package com.boostercloud.framework.provider.local.library;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The provider-neutral HTTP request the local provider receives as "raw request" (Java-only; TypeScript hands core
 * the Express {@code Request} object). The local infrastructure builds one per HTTP request and passes it to
 * {@code GraphQLService.handleGraphQLRequest} / {@code HealthService.handleHealthRequest}; the websocket server builds
 * one for the upgrade request of each connection.
 *
 * @param method the HTTP method
 * @param url the path and query string <b>relative to the router mount point</b>, like Express's {@code req.url}
 *     (for {@code GET /sensor/health/booster/database} the health router sees {@code /booster/database})
 * @param headers header names lower-cased (like Node), repeated headers joined with {@code ", "}
 * @param body the parsed JSON body as a raw value ({@code {}} when the body is not JSON, like Express), or
 *     {@code null}
 * @param rawBody the unparsed body bytes, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record LocalHttpRequest(String method, String url, Map<String, String> headers, Object body,
    @JsonIgnore byte[] rawBody) {

  /** Canonical constructor: lower-cases header names and makes the headers immutable. */
  public LocalHttpRequest {
    Map<String, String> normalized = new LinkedHashMap<>();
    if (headers != null) {
      headers.forEach((name, value) -> normalized.merge(name.toLowerCase(Locale.ROOT), value, (a, b) -> a + ", " + b));
    }
    headers = java.util.Collections.unmodifiableMap(normalized);
  }

  /**
   * @param method the method
   * @param url the mount-relative URL
   * @param headers the headers
   * @param body the parsed body
   * @return a request without raw body
   */
  public static LocalHttpRequest of(String method, String url, Map<String, String> headers, Object body) {
    return new LocalHttpRequest(method, url, headers, body, null);
  }

  /**
   * @param name a header name (any case)
   * @return its value, or {@code null}
   */
  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * @return the {@code authorization} header ({@code headers.authorization}), or {@code null}
   */
  public String authorization() {
    return header("authorization");
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof LocalHttpRequest that
        && Objects.equals(method, that.method)
        && Objects.equals(url, that.url)
        && headers.equals(that.headers)
        && Objects.equals(body, that.body)
        && Arrays.equals(rawBody, that.rawBody);
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, url, headers, body, Arrays.hashCode(rawBody));
  }

  @Override
  public String toString() {
    return "LocalHttpRequest[method=" + method + ", url=" + url + ", headers=" + headers + ", body=" + body
        + (rawBody == null ? "" : ", rawBody=" + rawBody.length + " bytes") + "]";
  }
}
