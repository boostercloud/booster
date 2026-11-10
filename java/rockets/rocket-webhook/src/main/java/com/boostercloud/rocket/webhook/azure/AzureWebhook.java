package com.boostercloud.rocket.webhook.azure;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.webhook.azure.functions.Functions;
import com.boostercloud.rocket.webhook.azure.synth.Synth;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.List;
import java.util.Objects;

/**
 * Port of {@code AzureWebhook} ({@code rocket-webhook-azure-infrastructure/src/index.ts}): the Azure infrastructure
 * rocket, {@code { mountStack, mountFunctionsV4, getFunctionAppName }} bound to the webhooks. There is no
 * {@code unmountStack} (the function app goes away with the resource group on {@code nuke}).
 */
public final class AzureWebhook implements InfrastructureRocket {

  private final List<WebhookParamsEvent> params;
  private final Synth synth;

  /**
   * @param params the webhooks
   */
  public AzureWebhook(List<WebhookParamsEvent> params) {
    this(params, new Synth());
  }

  /**
   * @param params the webhooks
   * @param synth the synthesis (tests pass deterministic rule names)
   */
  public AzureWebhook(List<WebhookParamsEvent> params, Synth synth) {
    this.params = Objects.requireNonNull(params, "params");
    this.synth = Objects.requireNonNull(synth, "synth");
  }

  /**
   * @return the webhooks
   */
  public List<WebhookParamsEvent> params() {
    return params;
  }

  @Override
  public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack applicationSynthStack,
      RocketUtils utils) {
    return synth.mountStack(params, config, applicationSynthStack, utils);
  }

  @Override
  public List<FunctionAppV4Definition> mountFunctionsV4(BoosterConfig config,
      ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    return Functions.mountFunctionsV4(params, config, applicationSynthStack, utils);
  }

  @Override
  public String getFunctionAppName(ApplicationSynthStack applicationSynthStack) {
    return Functions.getFunctionAppName(params, applicationSynthStack);
  }
}
