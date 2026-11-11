package com.boostercloud.rocket.webhook.core.response.responses;

import com.boostercloud.rocket.webhook.core.response.WebhookResponseClass;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookResponseType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code webhook-empty-response.ts}: the answer when the handler returned nothing (or a falsy value): an empty
 * {@code text/plain} body.
 */
public final class WebhookEmptyResponse implements WebhookResponseClass {

  /** Creates the strategy. */
  public WebhookEmptyResponse() {}

  @Override
  public Object getBody(WebhookHandlerReturnType response) {
    return "";
  }

  @Override
  public Map<String, Object> getHeaders(WebhookHandlerReturnType response) {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("Content-Type", WebhookResponseType.TEXT.value());
    return headers;
  }
}
