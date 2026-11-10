package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Port of {@code WebhookEvent} ({@code webhook-event.ts}): what a webhook handler receives. It is a plain request
 * description, not a Booster event (TypeScript declares it as an {@code EventInterface} but never gives it an
 * {@code entityID}); do not register it.
 *
 * <p>Example ({@code POST /test?param1=testvalue} with a JSON body, local provider):
 *
 * <pre>{@code
 * route        "test"
 * method       "POST"                       always "POST", also for GET requests (TypeScript behaviour)
 * url          "/test?param1=testvalue"     absolute on Azure
 * originalUrl  "/test?param1=testvalue"
 * headers      {host=localhost:3000, content-type=application/json, content-length=7, ...}
 * query        {param1=testvalue}
 * params       {}
 * rawBody      byte[] {"a":1}               local: JSON bodies only; Azure: the body text
 * body         {a=1}
 * multipart    null                         set for multipart/form-data requests
 * }</pre>
 *
 * @param origin the deprecated {@code origin} of the webhook, or {@code null}
 * @param route the configured route ({@code route ?? origin}), not the request path
 * @param method always {@code "POST"} (TypeScript hard-codes it)
 * @param url the request URL ({@code req.url})
 * @param originalUrl the original request URL ({@code req.originalUrl})
 * @param headers the request headers, lower-cased names
 * @param query the query parameters
 * @param params the route parameters
 * @param rawBody the raw body: {@code byte[]} (local, JSON bodies), {@code String} (Azure) or {@code null}
 * @param body the parsed body (see {@link WebhookHttpRequest})
 * @param multipart the parsed {@code multipart/form-data} parts, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookEvent(
    @Deprecated String origin,
    String route,
    String method,
    String url,
    String originalUrl,
    Map<String, String> headers,
    Map<String, Object> query,
    Map<String, String> params,
    Object rawBody,
    Object body,
    WebhookMultipartForm multipart) {

  /**
   * @param name a header name (any case)
   * @return its value, or {@code null}
   */
  public String header(String name) {
    return headers == null ? null : headers.get(name.toLowerCase(java.util.Locale.ROOT));
  }

  /**
   * Java convenience for {@link #rawBody()}, whose type depends on the provider.
   *
   * @return the raw body as bytes ({@code String}s encoded as UTF-8), or {@code null}
   */
  public byte[] rawBodyBytes() {
    return switch (rawBody) {
      case byte[] bytes -> bytes.clone();
      case String text -> text.getBytes(StandardCharsets.UTF_8);
      case null, default -> null;
    };
  }

  /**
   * Java convenience for {@link #rawBody()}, whose type depends on the provider.
   *
   * @return the raw body as text (bytes decoded as UTF-8), or {@code null}
   */
  public String rawBodyText() {
    return switch (rawBody) {
      case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
      case String text -> text;
      case null, default -> null;
    };
  }
}
