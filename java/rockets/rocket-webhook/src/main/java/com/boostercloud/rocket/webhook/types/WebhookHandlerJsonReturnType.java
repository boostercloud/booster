package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Port of {@code WebhookHandlerJsonReturnType}: an {@code application/json} handler result. The body is sent as
 * {@code JSON.stringify(body)}; a falsy body ({@code null}, {@code false}, {@code 0}, {@code ""}) is sent as an empty
 * body, as in TypeScript.
 *
 * @param body the value to serialise
 * @param headers extra headers, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookHandlerJsonReturnType(Object body, Map<String, Object> headers)
    implements WebhookHandlerReturnType {

  /** Canonical constructor: copies the headers. */
  public WebhookHandlerJsonReturnType {
    headers = WebhookHandlerReturnType.copyHeaders(headers);
  }

  @Override
  public WebhookResponseType responseType() {
    return WebhookResponseType.JSON;
  }

  /**
   * @param newHeaders extra headers
   * @return a copy with those headers
   */
  public WebhookHandlerJsonReturnType withHeaders(Map<String, ?> newHeaders) {
    return new WebhookHandlerJsonReturnType(body, WebhookHandlerReturnType.copyHeaders(newHeaders));
  }
}
