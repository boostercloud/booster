package com.boostercloud.rocket.webhook.core.response.responses;

import com.boostercloud.rocket.webhook.core.Js;
import com.boostercloud.rocket.webhook.core.response.WebhookResponseClass;
import com.boostercloud.rocket.webhook.types.WebhookHandlerFileReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code webhook-file-response.ts}: file downloads (and, as in TypeScript, results whose response type is
 * unknown). The body is sent unchanged with {@code Content-Type: <file.mimeType || ''>} and
 * {@code Content-Disposition: attachment; filename=<file.name>} (neither quoted nor encoded; a missing name is
 * {@code undefined}).
 */
public final class WebhookFileResponse implements WebhookResponseClass {

  /** Creates the strategy. */
  public WebhookFileResponse() {}

  @Override
  public Object getBody(WebhookHandlerReturnType response) {
    return response == null ? null : response.body();
  }

  /**
   * @throws IllegalArgumentException {@code Cannot read properties of undefined (reading 'mimeType')} when the
   *     result has no {@code file} (TypeScript's {@code TypeError})
   */
  @Override
  public Map<String, Object> getHeaders(WebhookHandlerReturnType response) {
    WebhookHandlerFileReturnType.File file =
        response instanceof WebhookHandlerFileReturnType fileResult ? fileResult.file() : null;
    if (file == null) {
      throw new IllegalArgumentException("Cannot read properties of undefined (reading 'mimeType')");
    }
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("Content-Type", Js.isTruthy(file.mimeType()) ? file.mimeType() : "");
    headers.put("Content-Disposition", "attachment; filename=" + Js.string(file.name()));
    return headers;
  }
}
