package com.boostercloud.rocket.webhook.types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of the {@code WebhookHandlerReturnType} union ({@code webhook-handler.ts}): the result of a webhook handler,
 * discriminated by {@link #responseType()}. A handler may also return nothing ({@code void} / {@code null}), which
 * answers {@code 200} with an empty {@code text/plain} body.
 *
 * <pre>{@code
 * return WebhookHandlerReturnType.json(Map.of("name", "my_name"));
 * return WebhookHandlerReturnType.text("ok").withHeaders(Map.of("Test", "test header"));
 * return WebhookHandlerReturnType.file(bytes, "1.txt", "text/plain");
 * }</pre>
 */
public sealed interface WebhookHandlerReturnType extends WebhookHandlerBaseReturnType
    permits WebhookHandlerTextReturnType, WebhookHandlerJsonReturnType, WebhookHandlerFileReturnType {

  /**
   * @return the discriminator ({@code responseType})
   */
  WebhookResponseType responseType();

  /**
   * @param body the body, sent as is
   * @return a {@code text/plain} result
   */
  static WebhookHandlerTextReturnType text(Object body) {
    return new WebhookHandlerTextReturnType(body, null);
  }

  /**
   * @param body the body, sent as {@code JSON.stringify(body)} (an empty body when it is falsy)
   * @return an {@code application/json} result
   */
  static WebhookHandlerJsonReturnType json(Object body) {
    return new WebhookHandlerJsonReturnType(body, null);
  }

  /**
   * @param body the content (bytes or a string)
   * @param name the file name ({@code Content-Disposition: attachment; filename=<name>})
   * @param mimeType the {@code Content-Type}
   * @return a file result
   */
  static WebhookHandlerFileReturnType file(Object body, String name, String mimeType) {
    return new WebhookHandlerFileReturnType(body, null, new WebhookHandlerFileReturnType.File(name, mimeType));
  }

  /**
   * @param headers response headers, or {@code null}
   * @return an unmodifiable, insertion-ordered copy ({@code null} stays {@code null}, {@code null} values are dropped)
   */
  static Map<String, Object> copyHeaders(Map<String, ?> headers) {
    if (headers == null) {
      return null;
    }
    Map<String, Object> copy = new LinkedHashMap<>();
    headers.forEach((key, value) -> {
      if (value != null) {
        copy.put(key, value);
      }
    });
    return Collections.unmodifiableMap(copy);
  }
}
