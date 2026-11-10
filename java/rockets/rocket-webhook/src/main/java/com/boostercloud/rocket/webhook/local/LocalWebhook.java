package com.boostercloud.rocket.webhook.local;

import com.boostercloud.framework.provider.local.infrastructure.InfrastructureRocketMetadata;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructureRocket;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.List;
import java.util.Objects;

/**
 * Port of {@code LocalWebhook} ({@code rocket-webhook-local-infrastructure/src/index.ts}): the local infrastructure
 * rocket, {@code { mountStack: Infra.mountStack.bind(Infra, params) }}. The server metadata (port) is not used and
 * there is nothing to unmount.
 */
public final class LocalWebhook implements LocalInfrastructureRocket {

  private final List<WebhookParamsEvent> params;

  /**
   * @param params the webhooks
   */
  public LocalWebhook(List<WebhookParamsEvent> params) {
    this.params = Objects.requireNonNull(params, "params");
  }

  /**
   * @return the webhooks
   */
  public List<WebhookParamsEvent> params() {
    return params;
  }

  @Override
  public void mountStack(BoosterConfig config, Router router, InfrastructureRocketMetadata metadata) {
    Infra.mountStack(params, config, router);
  }
}
