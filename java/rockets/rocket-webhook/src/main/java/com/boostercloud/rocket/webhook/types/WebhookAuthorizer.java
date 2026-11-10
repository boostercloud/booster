package com.boostercloud.rocket.webhook.types;

import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code WebhookAuthorizer} ({@code webhook-params.ts}): a custom authorization function for a webhook,
 * {@code (currentUser?, request?) => Promise<void>}. Returning normally grants access; throwing denies it, and the
 * thrown error decides the HTTP status of the answer ({@code NotAuthorizedError} gives 401, a plain exception 500).
 *
 * <pre>{@code
 * WebhookParamsEvent.builder().route("custom").handlerClass(CustomHandler.class)
 *     .authorize((currentUser, request) -> {
 *       if (currentUser == null || !"test@test.com".equals(currentUser.username())) {
 *         throw new NotAuthorizedError("Unexpected user");
 *       }
 *     })
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface WebhookAuthorizer {

  /**
   * @param currentUser the user of the request's token, or {@code null} for anonymous requests
   * @param request the rocket request
   */
  void authorize(UserEnvelope currentUser, WebhookRequest request);
}
