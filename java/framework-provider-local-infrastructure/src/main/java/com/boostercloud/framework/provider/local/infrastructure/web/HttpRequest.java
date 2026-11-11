package com.boostercloud.framework.provider.local.infrastructure.web;

import com.boostercloud.framework.provider.local.library.LocalHttpRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An HTTP request as the handlers of the local server see it: the Java counterpart of the Express {@code Request}
 * that the TypeScript controllers and rockets receive.
 *
 * <ul>
 *   <li>{@link #url()} is relative to the router the handler is mounted on (Express {@code req.url}): a handler
 *       mounted with {@code router.use("/sensor/health", ...)} sees {@code /booster} for
 *       {@code GET /sensor/health/booster}. {@link #originalUrl()} and {@link #baseUrl()} are the full URL and the
 *       mount path.
 *   <li>{@link #headers()} have lower-cased names; repeated headers are joined like Node does ({@code ", "}, cookies
 *       with {@code "; "}, and only the first value of single-valued headers such as {@code content-type}).
 *   <li>{@link #body()} is what the body parsers produced: a raw JSON value, a parsed form, {@code byte[]} for
 *       {@link BodyParsers#raw}, or an empty object when no parser handled the request (Express sets
 *       {@code req.body = {}}). {@link #rawBody()} holds the bytes of JSON bodies (TypeScript's {@code req.rawBody}).
 *   <li>{@link #inputStream()} gives the unread body, for handlers that stream it (TypeScript {@code req.pipe}).
 * </ul>
 *
 * <p>Instances are created by the server for every request, or with {@link #builder(String, String)} in tests.
 */
public final class HttpRequest {

  private static final Set<String> SINGLE_VALUE_HEADERS = Set.of("age", "authorization", "content-length",
      "content-type", "etag", "expires", "from", "host", "if-modified-since", "if-unmodified-since", "last-modified",
      "location", "max-forwards", "proxy-authorization", "referer", "retry-after", "server", "user-agent");

  private final String method;
  private final String originalUrl;
  private final Map<String, String> headers;
  private final String remoteAddress;
  private final InputStream body;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();
  private String url;
  private String baseUrl = "";
  private Map<String, String> params = Map.of();
  private Map<String, Object> query;
  private Object parsedBody;
  private byte[] rawBody;
  private boolean bodyRead;

  private HttpRequest(Builder builder) {
    this.method = builder.method.toUpperCase(Locale.ROOT);
    this.originalUrl = builder.url;
    this.url = builder.url;
    this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
    this.remoteAddress = builder.remoteAddress;
    this.body = builder.body == null ? InputStream.nullInputStream() : builder.body;
  }

  /**
   * @param method the HTTP method
   * @param url the path and query string ({@code /graphql?x=1})
   * @return a builder
   */
  public static Builder builder(String method, String url) {
    return new Builder(method, url);
  }

  /** Builds requests (the server, tests). */
  public static final class Builder {
    private final String method;
    private final String url;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String remoteAddress;
    private InputStream body;

    private Builder(String method, String url) {
      this.method = Objects.requireNonNull(method, "method");
      this.url = url == null || url.isEmpty() ? "/" : url;
    }

    /**
     * Adds a header with Node's rules for repeated headers.
     *
     * @param name the name (any case)
     * @param value the value
     * @return this builder
     */
    public Builder header(String name, String value) {
      String key = name.toLowerCase(Locale.ROOT);
      String existing = headers.get(key);
      if (existing == null) {
        headers.put(key, value);
      } else if (key.equals("cookie")) {
        headers.put(key, existing + "; " + value);
      } else if (!SINGLE_VALUE_HEADERS.contains(key)) {
        headers.put(key, existing + ", " + value);
      }
      return this;
    }

    /**
     * @param headerMap headers to add
     * @return this builder
     */
    public Builder headers(Map<String, String> headerMap) {
      headerMap.forEach(this::header);
      return this;
    }

    /**
     * @param address the client address
     * @return this builder
     */
    public Builder remoteAddress(String address) {
      this.remoteAddress = address;
      return this;
    }

    /**
     * @param stream the body
     * @return this builder
     */
    public Builder body(InputStream stream) {
      this.body = stream;
      return this;
    }

    /**
     * Sets the body and its {@code content-length} header.
     *
     * @param bytes the body
     * @return this builder
     */
    public Builder body(byte[] bytes) {
      this.body = new ByteArrayInputStream(bytes);
      if (!headers.containsKey("content-length") && !headers.containsKey("transfer-encoding")) {
        headers.put("content-length", Integer.toString(bytes.length));
      }
      return this;
    }

    /**
     * Sets a UTF-8 body, its {@code content-type} and its {@code content-length}.
     *
     * @param contentType the content type
     * @param text the body
     * @return this builder
     */
    public Builder body(String contentType, String text) {
      header("content-type", contentType);
      return body(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the request
     */
    public HttpRequest build() {
      return new HttpRequest(this);
    }
  }

  /**
   * @return the upper-cased HTTP method
   */
  public String method() {
    return method;
  }

  /**
   * @return the path and query string relative to the current mount point ({@code req.url})
   */
  public String url() {
    return url;
  }

  /**
   * @return the path and query string as received ({@code req.originalUrl})
   */
  public String originalUrl() {
    return originalUrl;
  }

  /**
   * @return the path of the mount point the current handler is under ({@code req.baseUrl}), {@code ""} at the top
   */
  public String baseUrl() {
    return baseUrl;
  }

  /**
   * @return the path of {@link #url()} without the query string ({@code req.path})
   */
  public String path() {
    return Uris.pathname(url);
  }

  /**
   * @return the route parameters ({@code req.params}): named ones by name, {@code *} wildcards as {@code "0"},
   *     {@code "1"}...; decoded
   */
  public Map<String, String> params() {
    return params;
  }

  /**
   * @param name a parameter name
   * @return its value, or {@code null}
   */
  public String param(String name) {
    return params.get(name);
  }

  /**
   * @return the parsed query string of the original URL ({@code req.query}, see {@link QueryStrings})
   */
  public synchronized Map<String, Object> query() {
    if (query == null) {
      query = Collections.unmodifiableMap(QueryStrings.parse(Uris.search(originalUrl)));
    }
    return query;
  }

  /**
   * @return the headers, names lower-cased
   */
  public Map<String, String> headers() {
    return headers;
  }

  /**
   * @param name a header name (any case)
   * @return its value, or {@code null}
   */
  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * @return the {@code content-type} header, or {@code null}
   */
  public String contentType() {
    return header("content-type");
  }

  /**
   * @param mime a mime type ({@code application/json}, {@code text/*})
   * @return whether the request has a body of that type ({@code req.is(type)} for requests with a body)
   */
  public boolean is(String mime) {
    return hasBody() && MimeTypes.is(contentType(), mime);
  }

  /**
   * {@code type-is}'s {@code hasBody}: a {@code transfer-encoding} header, or a numeric {@code content-length}.
   *
   * @return whether the request declares a body
   */
  public boolean hasBody() {
    if (headers.containsKey("transfer-encoding")) {
      return true;
    }
    String length = headers.get("content-length");
    if (length == null) {
      return false;
    }
    try {
      Double.parseDouble(length.trim().isEmpty() ? "0" : length.trim());
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * @return the client address, or {@code null}
   */
  public String remoteAddress() {
    return remoteAddress;
  }

  /**
   * @return the parsed body ({@code req.body}); an empty object once a body parser ran and found nothing to parse
   */
  public synchronized Object body() {
    return parsedBody;
  }

  /**
   * @param value the new body (what a body parser produced)
   */
  public synchronized void body(Object value) {
    this.parsedBody = value;
  }

  /**
   * @return the raw bytes of a JSON body ({@code req.rawBody}), or {@code null}
   */
  public synchronized byte[] rawBody() {
    return rawBody;
  }

  /**
   * @param bytes the raw body bytes
   */
  public synchronized void rawBody(byte[] bytes) {
    this.rawBody = bytes;
  }

  /**
   * @return whether a body parser already consumed the body ({@code req._body})
   */
  public synchronized boolean isBodyRead() {
    return bodyRead;
  }

  /**
   * Marks the body as consumed by a parser.
   */
  public synchronized void markBodyRead() {
    this.bodyRead = true;
  }

  /**
   * @return the body stream (empty once a parser consumed it)
   */
  public synchronized InputStream inputStream() {
    return bodyRead ? InputStream.nullInputStream() : body;
  }

  /**
   * Reads the rest of the body.
   *
   * @return the unread bytes
   * @throws UncheckedIOException when reading fails
   */
  public byte[] readAllBytes() {
    try {
      return inputStream().readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param name an attribute name
   * @return a value stored by a previous handler, or {@code null}
   */
  public Object attribute(String name) {
    return attributes.get(name);
  }

  /**
   * Stores a value for later handlers of the same request ({@code res.locals}-like).
   *
   * @param name the name
   * @param value the value ({@code null} removes it)
   */
  public void attribute(String name, Object value) {
    if (value == null) {
      attributes.remove(name);
    } else {
      attributes.put(name, value);
    }
  }

  /**
   * The provider-neutral request the local provider services take ({@code GraphQLService.handleGraphQLRequest},
   * {@code HealthService.handleHealthRequest}): method, mount-relative URL, headers, parsed body and raw bytes.
   *
   * @return the request for the provider
   */
  public LocalHttpRequest toLocalHttpRequest() {
    return new LocalHttpRequest(method, url, headers, body(), rawBody());
  }

  synchronized InputStream rawStream() {
    return body;
  }

  void url(String newUrl) {
    this.url = newUrl;
  }

  void baseUrl(String newBaseUrl) {
    this.baseUrl = newBaseUrl;
  }

  void params(Map<String, String> newParams) {
    this.params = newParams;
  }

  @Override
  public String toString() {
    return "HttpRequest[" + method + " " + originalUrl + "]";
  }
}
