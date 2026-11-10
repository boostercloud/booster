package com.boostercloud.framework.provider.azure.infrastructure.azure;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Sends HTTP requests: the replacement of the Azure SDK pipelines, {@code needle} and {@code fetch} used by the
 * TypeScript deploy flow. The production implementation is {@link JdkHttpTransport}; tests pass a recorder so they can
 * assert every call (method, URL, headers, body) without contacting Azure.
 */
@FunctionalInterface
public interface HttpTransport {

  /**
   * Sends a request and returns the response whatever its status.
   *
   * @param request the request
   * @return the response
   * @throws UncheckedIOException on transport failures (connection refused, timeout...)
   */
  Response send(Request request);

  /**
   * An HTTP request.
   *
   * @param method the verb
   * @param uri the URL
   * @param headers the headers (in order)
   * @param body the body: {@code null}, a {@code byte[]} or a {@link Path} streamed from disk
   * @param timeout the request timeout, or {@code null} for none
   */
  record Request(String method, URI uri, Map<String, String> headers, Object body, Duration timeout) {

    /** Canonical constructor: copies the headers and checks the body type. */
    public Request {
      Objects.requireNonNull(method, "method");
      Objects.requireNonNull(uri, "uri");
      headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
      if (body != null && !(body instanceof byte[]) && !(body instanceof Path)) {
        throw new IllegalArgumentException("A request body is a byte[] or a Path");
      }
    }

    /**
     * @param name a header name (case-insensitive)
     * @return the header value
     */
    public Optional<String> header(String name) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return Optional.of(entry.getValue());
        }
      }
      return Optional.empty();
    }

    /**
     * @return the body as UTF-8 text ({@code ""} without body; a file body is described by its path)
     */
    public String bodyText() {
      if (body instanceof byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
      }
      if (body instanceof Path path) {
        return "<file " + path + ">";
      }
      return "";
    }
  }

  /**
   * An HTTP response.
   *
   * @param status the status code
   * @param headers the headers with lower-case names
   * @param body the body bytes (empty when none)
   */
  record Response(int status, Map<String, List<String>> headers, byte[] body) {

    /** Canonical constructor: lower-cases and copies the headers. */
    public Response {
      Map<String, List<String>> copy = new LinkedHashMap<>();
      if (headers != null) {
        headers.forEach((name, values) -> copy.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
            .addAll(values));
      }
      headers = Collections.unmodifiableMap(copy);
      body = body == null ? new byte[0] : body;
    }

    /**
     * @param status the status code
     * @param body the body text
     * @param headers alternating header names and values
     * @return the response
     */
    public static Response of(int status, String body, String... headers) {
      Map<String, List<String>> map = new LinkedHashMap<>();
      for (int i = 0; i + 1 < headers.length; i += 2) {
        map.computeIfAbsent(headers[i], key -> new ArrayList<>()).add(headers[i + 1]);
      }
      return new Response(status, map, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param name a header name (case-insensitive)
     * @return its first value
     */
    public Optional<String> header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /**
     * @return the body as UTF-8 text
     */
    public String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }
}
