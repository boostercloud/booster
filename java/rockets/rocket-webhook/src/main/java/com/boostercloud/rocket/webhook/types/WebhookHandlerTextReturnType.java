package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Port of {@code WebhookHandlerTextReturnType}: a {@code text/plain} handler result. The body is sent as is
 * ({@code null} becomes an empty body).
 *
 * @param body the body
 * @param headers extra headers, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookHandlerTextReturnType(Object body, Map<String, Object> headers)
    implements WebhookHandlerReturnType {

  /** Canonical constructor: copies the headers. */
  public WebhookHandlerTextReturnType {
    headers = WebhookHandlerReturnType.copyHeaders(headers);
  }

  @Override
  public WebhookResponseType responseType() {
    return WebhookResponseType.TEXT;
  }

  /**
   * @param newHeaders extra headers
   * @return a copy with those headers
   */
  public WebhookHandlerTextReturnType withHeaders(Map<String, ?> newHeaders) {
    return new WebhookHandlerTextReturnType(body, WebhookHandlerReturnType.copyHeaders(newHeaders));
  }
}
