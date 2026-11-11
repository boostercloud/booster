package com.boostercloud.framework.provider.azure.types;

import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The HTTP request of an HTTP-triggered function: the subset of the Fetch-style {@code HttpRequest} of
 * {@code @azure/functions} v4 that Booster uses ({@code method}, {@code url}, {@code headers}, {@code json()},
 * {@code text()}, {@code query}, {@code params}).
 *
 * <p>Header names are lower-cased on construction, as Fetch {@code Headers} expose them; {@link #header(String)} is
 * case-insensitive like {@code headers.get(name)}.
 *
 * @param method the HTTP method in upper case ({@code POST})
 * @param url the full request URL
 * @param headers the headers (lower-cased names, insertion order kept)
 * @param body the raw body, or {@code null}
 * @param query the query parameters
 * @param params the route parameters
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record AzureHttpRequest(
    String method,
    String url,
    Map<String, String> headers,
    String body,
    Map<String, String> query,
    Map<String, String> params) {

  /** Canonical constructor: normalises header names and replaces {@code null} maps with empty ones. */
  public AzureHttpRequest {
    Map<String, String> normalized = new LinkedHashMap<>();
    if (headers != null) {
      headers.forEach((key, value) -> normalized.merge(
          key.toLowerCase(Locale.ROOT), value, (first, second) -> first + ", " + second));
    }
    headers = Collections.unmodifiableMap(normalized);
    query = query == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(query));
    params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
  }

  /**
   * @param method the method
   * @param url the URL
   * @param headers the headers
   * @param body the body
   * @return a request without query/route parameters
   */
  public static AzureHttpRequest of(String method, String url, Map<String, String> headers, String body) {
    return new AzureHttpRequest(method, url, headers, body, null, null);
  }

  /**
   * {@code request.headers.get(name)}.
   *
   * @param name a header name (any case)
   * @return the value, or {@code null} when absent
   */
  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * {@code request.headers.forEach((value, key) => ...)}.
   *
   * @param consumer receives {@code (value, key)}, keys in lower case
   */
  public void forEachHeader(BiConsumer<String, String> consumer) {
    headers.forEach((key, value) -> consumer.accept(value, key));
  }

  /**
   * {@code await request.json()}: parses the body.
   *
   * @return the raw JSON value
   * @throws java.io.UncheckedIOException when the body is empty or not JSON (Fetch rejects with a
   *     {@code SyntaxError})
   */
  public Object json() {
    if (body == null || body.isBlank()) {
      throw new java.io.UncheckedIOException(new java.io.IOException("Unexpected end of JSON input"));
    }
    return BoosterJson.parse(body);
  }

  /**
   * {@code await request.text()}.
   *
   * @return the body, or {@code ""} when there is none
   */
  public String text() {
    return body == null ? "" : body;
  }
}
