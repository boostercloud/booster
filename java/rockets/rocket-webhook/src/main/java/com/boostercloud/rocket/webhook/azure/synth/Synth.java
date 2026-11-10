package com.boostercloud.rocket.webhook.azure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Port of {@code synth/synth.ts}: the Terraform resources of the webhooks.
 *
 * <ol>
 *   <li>the function app ({@link TerraformFunctionApp});</li>
 *   <li>on the application gateway (when there is one), raw overrides that <b>replace</b> the gateway lists:
 *       {@code backend_address_pool} = the pool {@code rocketWebHookBackendAddressPool} pointing at
 *       {@code <functionApp.name>.azurewebsites.net}; {@code url_path_map.0.path_rule} = one rule per webhook for
 *       {@code /<route>} and {@code /<route>/*}; {@code rewrite_rule_set.0.rewrite_rule} = one rewrite per webhook
 *       ({@code rule_sequence} 1, 2...) from {@code /<route>} to {@code /api/<route>}.</li>
 * </ol>
 *
 * <p>Rule names are {@code toTerraformName(appPrefix, 'ru' | 'rw' + <first 23 characters of a random UUID>)}: new
 * names on every synthesis, as in TypeScript.
 */
public final class Synth {

  /** The backend pool of the webhook function app. */
  public static final String BACKEND_ADDRESS_POOL_ID = "rocketWebHookBackendAddressPool";

  private final Supplier<String> uuids;

  /** Random rule names ({@code UUID.generate()}). */
  public Synth() {
    this(() -> UUID.generate().toString());
  }

  /**
   * @param uuids the source of the UUIDs of the rule names (tests make them deterministic)
   */
  public Synth(Supplier<String> uuids) {
    this.uuids = uuids;
  }

  private record Names(String ruleName, String rewriteName, String route) {}

  /**
   * {@code mountStack(params, config, applicationSynthStack, utils)}.
   *
   * @param params the webhooks
   * @param config the configuration
   * @param applicationSynthStack the stack bag
   * @param utils the rocket utilities
   * @return the same stack bag
   * @throws IllegalStateException the errors of {@link TerraformFunctionApp#build}, or
   *     {@code Undefined route parameter [object Object]} for a webhook without route
   */
  public ApplicationSynthStack mountStack(List<WebhookParamsEvent> params, BoosterConfig config,
      ApplicationSynthStack applicationSynthStack, RocketUtils utils) {
    TerraformResource functionApp = TerraformFunctionApp.build(applicationSynthStack, config, utils);
    // As TypeScript: a new list is not stored back in the bag (`applicationSynthStack.rocketStack ?? []`)
    List<TerraformResource> rocketStack =
        applicationSynthStack.rocketStack != null ? applicationSynthStack.rocketStack : new ArrayList<>();
    rocketStack.add(functionApp);

    TerraformResource appGateway = applicationSynthStack.appGateway;
    List<Map<String, Object>> newBackendAddressPool = List.of(Attributes.of(
        "name", BACKEND_ADDRESS_POOL_ID,
        "fqdns", List.of(functionApp.ref("name") + ".azurewebsites.net")));
    if (appGateway != null) {
      appGateway.addOverride("backend_address_pool", newBackendAddressPool);
    }

    List<Names> names = new ArrayList<>();
    for (WebhookParamsEvent param : params) {
      String suffix = uuids.get().substring(0, 23);
      String ruleName = utils.toTerraformName(applicationSynthStack.appPrefix, "ru" + suffix);
      String rewriteName = utils.toTerraformName(applicationSynthStack.appPrefix, "rw" + suffix);
      String route = WebhookParams.getRoute(param);
      names.add(new Names(ruleName, rewriteName, route));
    }

    List<Map<String, Object>> pathRules = new ArrayList<>();
    for (Names name : names) {
      pathRules.add(Attributes.of(
          "name", name.ruleName(),
          "paths", List.of("/" + name.route(), "/" + name.route() + "/*"),
          "backend_address_pool_name", BACKEND_ADDRESS_POOL_ID,
          "backend_http_settings_name", "mainBackendHttpSettings",
          "rewrite_rule_set_name", "mainRewriteRuleSet"));
    }

    List<Map<String, Object>> rewritesRules = new ArrayList<>();
    for (int index = 0; index < names.size(); index++) {
      Names name = names.get(index);
      rewritesRules.add(Attributes.of(
          "name", name.rewriteName(),
          "rule_sequence", index + 1,
          "condition", List.of(Attributes.of(
              "pattern", "/" + name.route(),
              "variable", "var_request_uri",
              "ignore_case", true)),
          "url", Attributes.of(
              "path", "/api/" + name.route(),
              "components", "path_only")));
    }

    if (appGateway != null) {
      appGateway.addOverride("url_path_map.0.path_rule", new ArrayList<>(pathRules));
      appGateway.addOverride("rewrite_rule_set.0.rewrite_rule", new ArrayList<>(rewritesRules));
    }

    return applicationSynthStack;
  }
}
