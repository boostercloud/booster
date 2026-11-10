package com.boostercloud.rocket.webhook.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.rocket.webhook.types.WebhookRequest;

/**
 * Port of {@code webhook-authorization-checker.ts}: runs the authorizer of a webhook. <b>A webhook without
 * {@code authorize} is public</b>: nothing is checked (commands and queries without {@code authorize} deny access).
 */
public final class WebhookAuthorizationChecker {

  private WebhookAuthorizationChecker() {}

  /**
   * {@code authorize(config, authorizer, userEnvelope, request)}.
   *
   * @param config the configuration (unused, as in TypeScript)
   * @param authorizer the webhook's {@code authorize} option, or {@code null}
   * @param userEnvelope the user of the request's token, or {@code null}
   * @param request the rocket request
   * @throws RuntimeException whatever the authorizer throws when access is denied
   */
  public static void authorize(
      BoosterConfig config, Authorize<?> authorizer, UserEnvelope userEnvelope, WebhookRequest request) {
    if (authorizer != null) {
      WebhookAuthorizerBuilder.build(authorizer).authorize(userEnvelope, request);
    }
  }
}
