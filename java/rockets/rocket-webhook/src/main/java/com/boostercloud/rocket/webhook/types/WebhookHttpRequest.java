package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The HTTP request of a webhook call: the provider-neutral form of TypeScript's {@code WebhookRequest.req}, which is
 * the Express {@code Request} locally and a plain object built by the generated function code on Azure. Each
 * transport fills it with what its TypeScript counterpart puts there:
 *
 * <table>
 *   <caption>Values per provider</caption>
 *   <tr><th>Member</th><th>Local</th><th>Azure</th></tr>
 *   <tr><td>{@code url}, {@code originalUrl}</td><td>path and query ({@code /test?a=1})</td><td>the absolute URL</td></tr>
 *   <tr><td>{@code query}</td><td>{@code qs}-parsed (nested maps and lists)</td><td>flat, last value wins</td></tr>
 *   <tr><td>{@code rawBody}</td><td>the bytes of JSON bodies ({@code byte[]}), else {@code null}</td>
 *       <td>the body as text</td></tr>
 *   <tr><td>{@code body}</td><td>parsed JSON / form, {@code byte[]} for {@code application/octet-stream}, else an
 *       empty map</td><td>{@code byte[]} for multipart, else the parsed JSON or the text</td></tr>
 *   <tr><td>{@code bodyStream}</td><td>the unread body (multipart bodies are read from it)</td><td>{@code null}</td></tr>
 * </table>
 *
 * @param method the upper-case HTTP method
 * @param url the request URL as the transport sees it ({@code req.url})
 * @param originalUrl the original request URL ({@code req.originalUrl})
 * @param headers the headers, lower-cased names
 * @param query the query parameters
 * @param params the route parameters (always empty for webhook routes)
 * @param rawBody the raw body ({@code byte[]} or {@code String}), or {@code null}
 * @param body the parsed body, or {@code null}
 * @param bodyStream the unread body as a stream (TypeScript {@code req.pipe}), or {@code null} when the body is
 *     buffered in {@code body}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookHttpRequest(
    String method,
    String url,
    String originalUrl,
    Map<String, String> headers,
    Map<String, Object> query,
    Map<String, String> params,
    Object rawBody,
    Object body,
    @JsonIgnore Supplier<InputStream> bodyStream) {

  /** Canonical constructor: lower-cases header names and replaces {@code null} maps with empty ones. */
  public WebhookHttpRequest {
    Map<String, String> normalized = new LinkedHashMap<>();
    if (headers != null) {
      headers.forEach((key, value) -> normalized.putIfAbsent(key.toLowerCase(Locale.ROOT), value));
    }
    headers = Collections.unmodifiableMap(normalized);
    query = query == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(query));
    params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
  }

  /**
   * @param name a header name (any case)
   * @return its value, or {@code null}
   */
  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Reads the raw request object the Azure entry point builds
   * ({@code { method, url, originalUrl, headers, query, params, rawBody, body }}).
   *
   * @param raw the map
   * @return the request
   */
  public static WebhookHttpRequest fromRaw(Map<?, ?> raw) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (raw.get("headers") instanceof Map<?, ?> rawHeaders) {
      rawHeaders.forEach((key, value) -> {
        if (key != null && value != null) {
          headers.put(String.valueOf(key), String.valueOf(value));
        }
      });
    }
    Map<String, Object> query = new LinkedHashMap<>();
    if (raw.get("query") instanceof Map<?, ?> rawQuery) {
      rawQuery.forEach((key, value) -> query.put(String.valueOf(key), value));
    }
    Map<String, String> params = new LinkedHashMap<>();
    if (raw.get("params") instanceof Map<?, ?> rawParams) {
      rawParams.forEach((key, value) -> params.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
    }
    Object rawStream = raw.get("bodyStream");
    Supplier<InputStream> bodyStream = null;
    if (rawStream instanceof InputStream stream) {
      bodyStream = () -> stream;
    } else if (rawStream instanceof Supplier<?> supplier) {
      bodyStream = () -> (InputStream) supplier.get();
    }
    return new WebhookHttpRequest(asString(raw.get("method")), asString(raw.get("url")),
        asString(raw.get("originalUrl")), headers, query, params, raw.get("rawBody"), raw.get("body"), bodyStream);
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * @param method the method
   * @param url the URL
   * @return a builder
   */
  public static Builder builder(String method, String url) {
    return new Builder(method, url);
  }

  /** Builds requests (transports and tests). */
  public static final class Builder {
    private final String method;
    private final String url;
    private String originalUrl;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, Object> query;
    private Map<String, String> params;
    private Object rawBody;
    private Object body;
    private Supplier<InputStream> bodyStream;

    private Builder(String method, String url) {
      this.method = method;
      this.url = url;
      this.originalUrl = url;
    }

    /**
     * @param value the original URL (the URL by default)
     * @return this builder
     */
    public Builder originalUrl(String value) {
      this.originalUrl = value;
      return this;
    }

    /**
     * @param name a header name
     * @param value its value
     * @return this builder
     */
    public Builder header(String name, String value) {
      headers.put(name.toLowerCase(Locale.ROOT), value);
      return this;
    }

    /**
     * @param values headers to add
     * @return this builder
     */
    public Builder headers(Map<String, String> values) {
      values.forEach(this::header);
      return this;
    }

    /**
     * @param value the query parameters
     * @return this builder
     */
    public Builder query(Map<String, Object> value) {
      this.query = value;
      return this;
    }

    /**
     * @param value the route parameters
     * @return this builder
     */
    public Builder params(Map<String, String> value) {
      this.params = value;
      return this;
    }

    /**
     * @param value the raw body
     * @return this builder
     */
    public Builder rawBody(Object value) {
      this.rawBody = value;
      return this;
    }

    /**
     * @param value the parsed body
     * @return this builder
     */
    public Builder body(Object value) {
      this.body = value;
      return this;
    }

    /**
     * @param value the unread body
     * @return this builder
     */
    public Builder bodyStream(Supplier<InputStream> value) {
      this.bodyStream = value;
      return this;
    }

    /**
     * @return the request
     */
    public WebhookHttpRequest build() {
      return new WebhookHttpRequest(method, url, originalUrl, headers, query, params, rawBody, body, bodyStream);
    }
  }
}
