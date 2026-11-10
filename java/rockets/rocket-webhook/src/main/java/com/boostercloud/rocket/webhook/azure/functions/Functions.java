package com.boostercloud.rocket.webhook.azure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.webhook.azure.Helper;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code functions/functions.ts}: the code of the webhook function app.
 */
public final class Functions {

  private Functions() {}

  /**
   * {@code mountFunctionsV4(params, config, applicationSynthStack, utils)}: one package for the function app
   * {@code <resourceGroupName>webhookfunc} with one HTTP function per webhook, in order, and the default
   * {@code host.json} (route prefix {@code api}).
   *
   * @param params the webhooks
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param utils the rocket utilities (unused)
   * @return exactly one definition
   * @throws IllegalStateException {@code Undefined route parameter [object Object]} for a webhook without route
   */
  public static List<FunctionAppV4Definition> mountFunctionsV4(List<WebhookParamsEvent> params, BoosterConfig config,
      ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    String functionAppName = Helper.getFunctionAppName(applicationSynthStack.resourceGroupName);
    List<FunctionDefinition> registrations = new ArrayList<>();
    for (WebhookParamsEvent param : params) {
      registrations.add(WebhookFunction.generateFunctionsCode(config, WebhookParams.getRoute(param)));
    }
    return List.of(FunctionAppV4Definition.of(functionAppName, registrations));
  }

  /**
   * {@code getFunctionAppName(params, applicationSynthStack)}.
   *
   * @param params the webhooks (unused)
   * @param applicationSynthStack the stack bag
   * @return {@code <resourceGroupName>webhookfunc}
   */
  public static String getFunctionAppName(List<WebhookParamsEvent> params, ApplicationSynthStack applicationSynthStack) {
    return Helper.getFunctionAppName(applicationSynthStack.resourceGroupName);
  }
}
