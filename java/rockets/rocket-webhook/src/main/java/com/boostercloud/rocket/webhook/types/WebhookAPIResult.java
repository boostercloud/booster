package com.boostercloud.rocket.webhook.types;

import java.util.Map;

/**
 * Port of {@code WebhookAPIResult} ({@code webhook-api-result.ts}): what the rocket function returns to the
 * transport ({@code boosterRocketDispatcher}'s result). The transports send {@link #status()}, the
 * {@link #headers()} (success only, locally) and the {@link #body()}.
 *
 * <p>TypeScript's {@code Headers} type ({@code Record<string, number | string | ReadonlyArray<string>>}) is an
 * insertion-ordered {@code Map<String, Object>}.
 */
public sealed interface WebhookAPIResult permits WebhookAPISuccessResult, WebhookAPIErrorResult {

  /** {@code HttpSuccessStatusCode}: the status of every successful webhook call. */
  int HTTP_SUCCESS_STATUS_CODE = 200;

  /**
   * @return the HTTP status
   */
  int status();

  /**
   * @return the body: a string, bytes or any value for successes, {@code { title, reason }} for failures
   */
  Object body();

  /**
   * @return the response headers
   */
  Map<String, Object> headers();
}
