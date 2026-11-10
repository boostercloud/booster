package com.boostercloud.rocket.webhook.core.response.responses;

import com.boostercloud.rocket.webhook.core.response.WebhookResponseClass;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookResponseType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code webhook-text-response.ts}: {@code text/plain} results. The body is sent as is; only {@code null}
 * becomes the empty string ({@code response?.body ?? ''}).
 */
public final class WebhookTextResponse implements WebhookResponseClass {

  /** Creates the strategy. */
  public WebhookTextResponse() {}

  @Override
  public Object getBody(WebhookHandlerReturnType response) {
    Object body = response == null ? null : response.body();
    return body != null ? body : "";
  }

  @Override
  public Map<String, Object> getHeaders(WebhookHandlerReturnType response) {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("Content-Type", WebhookResponseType.TEXT.value());
    return headers;
  }
}
