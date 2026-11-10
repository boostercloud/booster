package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import static com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes.of;

import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code synth/gateway/terraform-application-gateway.ts}: the {@code azurerm_application_gateway} (construct id
 * {@code ag}) that exposes the main function app on plain HTTP port 80:
 * {@code /<env>/graphql} → {@code /api/graphql} and {@code /<env>/sensor/health[/...]} →
 * {@code /api/sensor/health[/...]}, where {@code <env>} is {@code BOOSTER_ENV} (default {@code azure}). With
 * {@code BOOSTER_USE_WAF=true} the SKU is {@code WAF_v2} with a detection-mode WAF configuration, otherwise
 * {@code Standard_v2}.
 */
public final class TerraformApplicationGateway {

  /** The default SKU. */
  public static final String STANDARD_V2 = "Standard_v2";
  /** The SKU with {@code BOOSTER_USE_WAF=true}. */
  public static final String WAF_V2 = "WAF_v2";

  /** The backend pool targeting the main function app. */
  public static final String BACKEND_ADDRESS_POOL = "functionManagementBackendAddressPool";
  /** The backend HTTP settings. */
  public static final String BACKEND_HTTP_SETTINGS = "mainBackendHttpSettings";
  /** The rewrite rule set. */
  public static final String REWRITE_RULE_SET = "mainRewriteRuleSet";

  private TerraformApplicationGateway() {}

  /**
   * @param stack the stack bag
   * @return the resource
   * @throws IllegalStateException {@code Undefined virtualNetwork resource} / {@code Undefined subnet resource} /
   *     {@code Undefined publicIP resource}, in that order
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    String environment = EnvironmentVariables.get("BOOSTER_ENV").orElse("azure");
    if (stack.virtualNetwork == null) {
      throw new IllegalStateException("Undefined virtualNetwork resource");
    }
    if (stack.subnet == null) {
      throw new IllegalStateException("Undefined subnet resource");
    }
    if (stack.publicIP == null) {
      throw new IllegalStateException("Undefined publicIP resource");
    }
    boolean useWaf = "true".equals(Constants.useWaf());
    String skuType = useWaf ? WAF_V2 : STANDARD_V2;
    Map<String, Object> configuration = of(
        "name", stack.resourceGroupName + "apigw",
        "location", stack.resourceGroup.ref("location"),
        "resource_group_name", stack.resourceGroupName,
        "sku", of("name", skuType, "tier", skuType, "capacity", 1),
        "gateway_ip_configuration", List.of(of(
            "name", "myGatewayIpConfiguration",
            "subnet_id", stack.subnet.ref("id"))),
        "frontend_ip_configuration", List.of(of(
            "name", "myFrontendIpConfiguration",
            "public_ip_address_id", stack.publicIP.ref("id"))),
        "frontend_port", List.of(of("name", "myFrontendPort", "port", 80)),
        "backend_address_pool", List.of(of(
            "name", BACKEND_ADDRESS_POOL,
            "fqdns", List.of(stack.functionAppName + ".azurewebsites.net"))),
        "http_listener", List.of(of(
            "protocol", "Http",
            "name", "mainHttpListener",
            "frontend_ip_configuration_name", "myFrontendIpConfiguration",
            "frontend_port_name", "myFrontendPort")),
        "backend_http_settings", List.of(of(
            "name", BACKEND_HTTP_SETTINGS,
            "cookie_based_affinity", "Disabled",
            "port", 80,
            "protocol", "Http",
            "request_timeout", 20,
            "pick_host_name_from_backend_address", true)),
        "url_path_map", List.of(of(
            "name", "mainUrlPathMap",
            "default_backend_address_pool_name", BACKEND_ADDRESS_POOL,
            "default_backend_http_settings_name", BACKEND_HTTP_SETTINGS,
            "default_rewrite_rule_set_name", REWRITE_RULE_SET,
            "path_rule", List.of(
                of(
                    "name", "graphQLRule",
                    "paths", List.of("/" + environment + "/graphql/*"),
                    "backend_address_pool_name", BACKEND_ADDRESS_POOL,
                    "backend_http_settings_name", BACKEND_HTTP_SETTINGS),
                of(
                    "name", "sensorRule",
                    "paths", List.of("/" + environment + "/sensor"),
                    "backend_address_pool_name", BACKEND_ADDRESS_POOL,
                    "backend_http_settings_name", BACKEND_HTTP_SETTINGS)))),
        "rewrite_rule_set", List.of(of(
            "name", REWRITE_RULE_SET,
            "rewrite_rule", List.of(
                of(
                    "name", "graphQLRewriteRule",
                    "rule_sequence", 100,
                    "condition", List.of(of(
                        "pattern", "/" + environment + "/graphql",
                        "variable", "var_request_uri",
                        "ignore_case", true)),
                    "url", of("path", "/api/graphql", "components", "path_only")),
                of(
                    "name", "sensorRewriteRule",
                    "rule_sequence", 101,
                    "condition", List.of(of(
                        "pattern", "/" + environment + "/sensor/health(/.*)?",
                        "variable", "var_request_uri",
                        "ignore_case", true)),
                    "url", of("path", "/api/sensor/health{var_request_uri_1}", "components", "path_only"))))),
        "request_routing_rule", List.of(of(
            "name", "mainRequestRoutingRule",
            "rule_type", "PathBasedRouting",
            "http_listener_name", "mainHttpListener",
            "backend_address_pool_name", BACKEND_ADDRESS_POOL,
            "backend_http_settings_name", BACKEND_HTTP_SETTINGS,
            "priority", 100,
            "url_path_map_name", "mainUrlPathMap",
            "rewrite_rule_set_name", REWRITE_RULE_SET)));
    if (useWaf) {
      configuration.put("waf_configuration", of("enabled", true, "firewall_mode", "Detection", "rule_set_version", "3.0"));
    }
    return new TerraformResource(stack.terraformStack, "ag", "azurerm_application_gateway")
        .setAttributes(configuration)
        .provider(stack.azureProvider);
  }
}
