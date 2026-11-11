package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code WebhookAPIErrorResult}: the answer to a failed request (unknown route, method not allowed, token or
 * authorization failure, handler or storage error). Components in the order TypeScript builds the object
 * ({@code { status, body, headers }}).
 *
 * @param status {@code httpStatusCodeFor(error)}: 400, 401, 404, 422 for the Booster errors that have one, else 500
 * @param body {@code { title, reason }}
 * @param headers {@code Access-Control-Allow-Origin: *} and {@code Content-Type: application/json}
 */
public record WebhookAPIErrorResult(int status, Body body, Map<String, Object> headers) implements WebhookAPIResult {

  /**
   * The failure body.
   *
   * @param title {@code toClassTitle(error)} ({@code Invalid Parameter Error})
   * @param reason the error message ({@code ""} when it has none, like a JavaScript {@code Error})
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Body(String title, String reason) {}

  /** Canonical constructor: copies the headers. */
  public WebhookAPIErrorResult {
    headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
  }
}
