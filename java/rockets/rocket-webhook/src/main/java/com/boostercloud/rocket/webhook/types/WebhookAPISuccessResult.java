package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code WebhookAPISuccessResult}: the answer to a request whose handler returned (the status is always
 * {@value WebhookAPIResult#HTTP_SUCCESS_STATUS_CODE}). Components in the order TypeScript builds the object
 * ({@code { headers, status, body }}).
 *
 * @param headers the response headers (default, handler and type-specific ones, merged in that order)
 * @param status always 200
 * @param body the body ({@code String}, {@code byte[]} or the handler's value)
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WebhookAPISuccessResult(Map<String, Object> headers, int status, Object body) implements WebhookAPIResult {

  /** Canonical constructor: copies the headers and checks the status. */
  public WebhookAPISuccessResult {
    if (status != HTTP_SUCCESS_STATUS_CODE) {
      throw new IllegalArgumentException("A webhook success result has status " + HTTP_SUCCESS_STATUS_CODE);
    }
    headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
  }

  /**
   * @param headers the headers
   * @param body the body
   * @return a 200 result
   */
  public static WebhookAPISuccessResult of(Map<String, Object> headers, Object body) {
    return new WebhookAPISuccessResult(headers, HTTP_SUCCESS_STATUS_CODE, body);
  }
}
