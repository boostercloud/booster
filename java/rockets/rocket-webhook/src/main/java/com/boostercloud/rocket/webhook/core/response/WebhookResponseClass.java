package com.boostercloud.rocket.webhook.core.response;

import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import java.util.Map;

/**
 * Port of {@code WebhookResponseClass} ({@code webhook-response-builder.ts}): how one kind of handler result is sent.
 */
public interface WebhookResponseClass {

  /**
   * @param response the handler result, or {@code null} when the handler returned nothing
   * @return the response body
   */
  Object getBody(WebhookHandlerReturnType response);

  /**
   * @param response the handler result, or {@code null} when the handler returned nothing
   * @return the type-specific headers ({@code Content-Type}, {@code Content-Disposition}), which override every other
   */
  Map<String, Object> getHeaders(WebhookHandlerReturnType response);
}
