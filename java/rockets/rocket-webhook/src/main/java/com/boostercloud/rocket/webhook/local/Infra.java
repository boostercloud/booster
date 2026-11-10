package com.boostercloud.rocket.webhook.local;

import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.webhook.local.controllers.WebhookController;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.List;
import java.util.function.Function;

/**
 * Port of {@code infra.ts}: mounts one {@link WebhookController} per webhook, in order, at {@code /} of the local
 * server's router, so each webhook answers at {@code http://localhost:<port>/<route>} (no prefix). The router already
 * has {@code /sensor/health} and {@code /graphql}, which win over webhooks with those routes.
 */
public final class Infra {

  private Infra() {}

  /**
   * {@code mountStack(params, config, router)}.
   *
   * @param params the webhooks
   * @param config the configuration (unused, as in TypeScript)
   * @param router the local server's router
   * @throws IllegalStateException {@code Undefined route parameter [object Object]} for a webhook without route (the
   *     server does not start)
   */
  public static void mountStack(List<WebhookParamsEvent> params, BoosterConfig config, Router router) {
    for (WebhookParamsEvent parameter : params) {
      router.use("/", new WebhookController(WebhookParams.getRoute(parameter)).router());
    }
  }

  /**
   * {@link #mountStack} with another {@code boosterRocketDispatcher} (tests).
   *
   * @param params the webhooks
   * @param router the router
   * @param boosterRocketDispatcher the dispatcher the controllers call
   */
  static void mountStack(List<WebhookParamsEvent> params, Router router, Function<Object, Object> boosterRocketDispatcher) {
    for (WebhookParamsEvent parameter : params) {
      router.use("/", new WebhookController(WebhookParams.getRoute(parameter), boosterRocketDispatcher).router());
    }
  }
}
