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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
   * <p>Two routes that differ only in characters outside {@code [a-zA-Z0-9_]} ({@code a/b} and {@code a_b}) get the
   * same function name. TypeScript generates two {@code app.http("a_b", ...)} registrations and the Node worker
   * refuses the second one when the function app starts ({@code A function with name "a_b" has already been
   * registered.}), so no webhook of the app works; a Java package has one {@code function.json} per name, so the
   * collision is reported here, at synthesis, with the worker's message.
   *
   * @param params the webhooks
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param utils the rocket utilities (unused)
   * @return exactly one definition
   * @throws IllegalStateException {@code Undefined route parameter [object Object]} for a webhook without route, or
   *     {@code A function with name "<name>" has already been registered.} for colliding routes
   */
  public static List<FunctionAppV4Definition> mountFunctionsV4(List<WebhookParamsEvent> params, BoosterConfig config,
      ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    String functionAppName = Helper.getFunctionAppName(applicationSynthStack.resourceGroupName);
    List<FunctionDefinition> registrations = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (WebhookParamsEvent param : params) {
      FunctionDefinition registration = WebhookFunction.generateFunctionsCode(config, WebhookParams.getRoute(param));
      if (!names.add(registration.name())) {
        throw new IllegalStateException(
            "A function with name \"" + registration.name() + "\" has already been registered.");
      }
      registrations.add(registration);
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
