package com.boostercloud.rocket.webhook.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.List;
import java.util.Objects;

/**
 * Port of {@code BoosterWebhook} ({@code rocket-webhook-core/src/index.ts}): the entry point of the webhook rocket.
 * Creating it registers the rocket function {@value WebhookParams#FUNCTION_ID} in the configuration (so all the
 * webhooks of an application go in <b>one</b> {@code BoosterWebhook}: a second one on the same configuration fails),
 * and its descriptors tell the provider infrastructure which routes to create.
 *
 * <pre>{@code
 * public class Config implements BoosterConfigurator {
 *   @Override
 *   public void configure() {
 *     Booster.configure("local", config -> {
 *       config.appName = "my-store";
 *       config.providerPackage = "com.boostercloud.framework.provider.local";
 *       config.rockets = List.of(new BoosterWebhook(config, List.of(
 *           WebhookParamsEvent.builder().route("test").handlerClass(TestHandler.class)
 *               .allowedMethods(AllowedHttpMethod.POST).build(),
 *           WebhookParamsEvent.builder().route("clients/other").handlerClass(FacebookHandler.class)
 *               .allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST).build()))
 *           .rocketForLocal());                      // rocketForAzure() in the Azure environments
 *     });
 *   }
 * }
 * }</pre>
 */
public final class BoosterWebhook {

  /** The descriptor package of the local infrastructure ({@code @boostercloud/rocket-webhook-local-infrastructure}). */
  public static final String LOCAL_INFRASTRUCTURE_PACKAGE = "com.boostercloud.rocket.webhook.local";

  /** The descriptor package of the Azure infrastructure ({@code @boostercloud/rocket-webhook-azure-infrastructure}). */
  public static final String AZURE_INFRASTRUCTURE_PACKAGE = "com.boostercloud.rocket.webhook.azure";

  private final BoosterConfig config;
  private final List<WebhookParamsEvent> params;

  /**
   * Registers the rocket function. The parameters are not validated here (an empty list, missing or duplicated routes
   * are accepted), and the list is used by reference, as in TypeScript.
   *
   * @param config the configuration being built
   * @param params the webhooks
   * @throws IllegalStateException {@code Error registering rocket function with id rocket-webhook: There is already a
   *     rocket function registered under the same ID, "..."} when the configuration already has one
   */
  public BoosterWebhook(BoosterConfig config, List<WebhookParamsEvent> params) {
    this.config = Objects.requireNonNull(config, "config");
    this.params = Objects.requireNonNull(params, "params");
    config.registerRocketFunction(WebhookParams.FUNCTION_ID,
        (dispatcherConfig, request) -> WebhookDispatcher.dispatch(dispatcherConfig, request, params));
  }

  /**
   * @return the configuration given to the constructor
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * @return the webhooks (the instance given to the constructor)
   */
  public List<WebhookParamsEvent> params() {
    return params;
  }

  /**
   * {@code rocketForAzure()}.
   *
   * @return {@code { packageName: "com.boostercloud.rocket.webhook.azure", parameters: params }}
   */
  public RocketDescriptor rocketForAzure() {
    return new RocketDescriptor(AZURE_INFRASTRUCTURE_PACKAGE, params);
  }

  /**
   * {@code rocketForLocal()}.
   *
   * @return {@code { packageName: "com.boostercloud.rocket.webhook.local", parameters: params }}
   */
  public RocketDescriptor rocketForLocal() {
    return new RocketDescriptor(LOCAL_INFRASTRUCTURE_PACKAGE, params);
  }
}
