package com.boostercloud.rocket.webhook.local;

import com.boostercloud.framework.common.RocketBuilder;
import com.boostercloud.rocket.webhook.types.WebhookParams;

/**
 * The rocket builder of the local infrastructure, loaded by {@code RocketLoader} from the descriptor package
 * {@code com.boostercloud.rocket.webhook.local} ({@code BoosterWebhook.rocketForLocal()}); the Java form of the default
 * export of {@code @boostercloud/rocket-webhook-local-infrastructure}.
 */
public final class Rocket implements RocketBuilder {

  /** Created by {@code RocketLoader}. */
  public Rocket() {}

  /**
   * @param parameters the descriptor parameters: the {@code List<WebhookParamsEvent>} of {@code BoosterWebhook}
   * @return the {@link LocalWebhook}
   * @throws IllegalArgumentException when the parameters are not a list of webhooks
   */
  @Override
  public LocalWebhook build(Object parameters) {
    return new LocalWebhook(WebhookParams.from(parameters));
  }
}
