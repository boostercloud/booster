package com.boostercloud.rocket.webhook.core.response.responses;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.rocket.webhook.core.Js;
import com.boostercloud.rocket.webhook.core.response.WebhookResponseClass;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookResponseType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code webhook-json-response.ts}: {@code application/json} results. The body is
 * {@code JSON.stringify(body)} ({@link BoosterJson#toJson}); a falsy body ({@code null}, {@code false}, {@code 0},
 * {@code ""}) gives an empty body, as {@code body ? JSON.stringify(body) : ''} does.
 */
public final class WebhookJsonResponse implements WebhookResponseClass {

  /** Creates the strategy. */
  public WebhookJsonResponse() {}

  @Override
  public Object getBody(WebhookHandlerReturnType response) {
    Object body = response == null ? null : response.body();
    return Js.isTruthy(body) ? BoosterJson.toJson(body) : "";
  }

  @Override
  public Map<String, Object> getHeaders(WebhookHandlerReturnType response) {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("Content-Type", WebhookResponseType.JSON.value());
    return headers;
  }
}
