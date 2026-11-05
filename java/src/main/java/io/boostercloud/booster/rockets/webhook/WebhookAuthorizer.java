package io.boostercloud.booster.rockets.webhook;

import io.boostercloud.booster.core.UserContext;

@FunctionalInterface
public interface WebhookAuthorizer {
  /** Throw to deny access. Called on every attempt, including receipt replays. */
  void authorize(UserContext user, WebhookRequest request);
}
