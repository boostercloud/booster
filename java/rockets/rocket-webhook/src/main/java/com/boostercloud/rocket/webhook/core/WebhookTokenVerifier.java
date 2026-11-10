package com.boostercloud.rocket.webhook.core;

import com.boostercloud.framework.core.BoosterTokenVerifier;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.rocket.webhook.types.WebhookRequest;

/**
 * Port of {@code webhook-token-verifier.ts}: the user of a webhook request. The {@code authorization} header (with
 * its {@code Bearer } prefix) goes through every {@code config.tokenVerifiers} ({@link BoosterTokenVerifier}); <b>it
 * is verified whenever it is present</b>, also for webhooks without {@code authorize}, so a bad token fails the request
 * with 401 ({@code NotAuthorizedError}, {@code BoosterTokenExpiredError}, {@code BoosterTokenNotBeforeError}).
 */
public final class WebhookTokenVerifier {

  private WebhookTokenVerifier() {}

  /**
   * {@code verify(config, request)}.
   *
   * @param config the configuration
   * @param request the rocket request
   * @return the user, or {@code null} without (or with an empty) {@code authorization} header
   */
  public static UserEnvelope verify(BoosterConfig config, WebhookRequest request) {
    BoosterTokenVerifier boosterTokenVerifier = new BoosterTokenVerifier(config);
    String token = request == null || request.req() == null ? null : request.req().headers().get("authorization");
    if (!Js.isTruthy(token)) {
      return null;
    }
    return boosterTokenVerifier.verify(token);
  }
}
