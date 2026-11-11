package com.boostercloud.rocket.webhook.azure;

import com.boostercloud.framework.common.RocketBuilder;
import com.boostercloud.rocket.webhook.types.WebhookParams;

/**
 * The rocket builder of the Azure infrastructure, loaded by {@code RocketLoader} from the descriptor package
 * {@code com.boostercloud.rocket.webhook.azure} ({@code BoosterWebhook.rocketForAzure()}); the Java form of the default
 * export of {@code @boostercloud/rocket-webhook-azure-infrastructure}.
 */
public final class Rocket implements RocketBuilder {

  /** Created by {@code RocketLoader}. */
  public Rocket() {}

  /**
   * @param parameters the descriptor parameters: the {@code List<WebhookParamsEvent>} of {@code BoosterWebhook}
   * @return the {@link AzureWebhook}
   * @throws IllegalArgumentException when the parameters are not a list of webhooks
   */
  @Override
  public AzureWebhook build(Object parameters) {
    return new AzureWebhook(WebhookParams.from(parameters));
  }
}
