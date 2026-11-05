package io.boostercloud.booster.rockets.webhook;

import io.boostercloud.booster.core.Register;

@FunctionalInterface
public interface WebhookHandler {
  /** Return null for the baseline empty success response. Buffered events commit after this returns. */
  WebhookResponse handle(WebhookRequest request, Register register) throws Exception;
}
