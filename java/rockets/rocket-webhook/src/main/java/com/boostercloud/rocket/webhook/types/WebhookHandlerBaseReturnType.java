package com.boostercloud.rocket.webhook.types;

import java.util.Map;

/**
 * Port of {@code WebhookHandlerBaseReturnType} ({@code webhook-handler.ts}): what every handler result has.
 */
public interface WebhookHandlerBaseReturnType {

  /**
   * @return the response body ({@code unknown}: a string, bytes, or any JSON-serialisable value)
   */
  Object body();

  /**
   * @return extra response headers ({@code number | string | List<String>} values), or {@code null}; they override
   *     the default {@code Access-Control-Allow-Origin: *} and are overridden by the type-specific headers
   *     ({@code Content-Type}, {@code Content-Disposition})
   */
  Map<String, Object> headers();
}
