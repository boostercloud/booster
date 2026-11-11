package com.boostercloud.framework.provider.azure.infrastructure.synth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformApplicationGateway;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformNetworkSecurityGroup;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformPublicIpData;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformSubnet;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformSubnetSecurity;
import com.boostercloud.framework.provider.azure.infrastructure.synth.gateway.TerraformVirtualNetwork;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformApp;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.io.TempDir;

/** The resource builders beyond the golden files: guards, names, conditions and environment knobs. */
@ExtendWith(TestEnvironment.class)
class ApplicationSynthTest {

  @TempDir Path root;

  private ApplicationSynth synth(BoosterConfig config) {
    return new ApplicationSynth(new TerraformStack(new TerraformApp(root), config.appName + config.environmentName),
        config, Fixtures.CONFIGURATION, Fixtures.APP_CLASS);
  }

  private ApplicationSynthStack emptyStack() {
    ApplicationSynth synth = synth(Fixtures.config());
    return new ApplicationSynthStack(synth.stackNames(), TerraformResourceGroup.build(synth.stackNames()));
  }

  private static void assertUndefined(ThrowingCallable call, String resource) {
    assertThatThrownBy(call).isInstanceOf(IllegalStateException.class).hasMessage("Undefined " + resource + " resource");
  }

  @Test
  void computes_the_stack_names() {
    ApplicationSynth synth = synth(Fixtures.config());
    assertThat(synth.stackNames().appPrefix).isEqualTo("mystoreproduction");
    assertThat(synth.stackNames().resourceGroupName).isEqualTo("mystoreproductionrg");
    assertThat(synth.stackNames().functionAppName).isEqualTo("mystoreproductionrgfpr");
    assertThat(synth.stackNames().streamFunctionAppName).isEqualTo("mystoreproductionrgfcs");
    assertThat(synth.stackNames().domainNameLabel).isEqualTo("mystoreproductionrgapis");
    assertThat(synth.stackNames().eventHubName).isEqualTo("booster_events");
    assertThat(synth.stackNames().webPubSubHubName).isEqualTo("booster");
    assertThat(synth.stackNames().boosterAppClass).isEqualTo(Fixtures.APP_CLASS);
    assertThat(synth.stackNames().azureProvider.attributes()).isEqualTo(Map.of(
        "features", List.of(Map.of()), "subscription_id", "subscription-id",
        "resource_provider_registrations", "legacy"));
  }

  @Test
  void fills_the_stack_bag() {
    ApplicationSynthStack stack = synth(Fixtures.streamingConfig()).synth("/tmp/base.zip");
    assertThat(stack.containers).extracting(TerraformResource::logicalId).containsExactly("mystoreproductionevents",
        "oductiondispatchedevents", "mystoreproductionsubscr", "oductionconnectionstable", "cartreadmodel",
        "productreadmodel", "toreproductiondeduptable");
    assertThat(stack.eventConsumerServicePlan.attribute("worker_count")).isEqualTo(4);
    assertThat(stack.functionApp.attribute("zip_deploy_file")).isEqualTo("/tmp/base.zip");
    assertThat(stack.eventConsumerFunctionApp.attribute("zip_deploy_file")).isNull();
    assertThat(stack.webPubSubHub).isNotNull();
    assertThat(stack.rocketStack).isNull();
  }

  @Test
  void without_subscriptions_the_dispatched_events_container_is_not_in_the_bag() {
    ApplicationSynthStack stack = synth(Fixtures.noSubscriptionsConfig()).synth(null);
    assertThat(stack.containers).extracting(TerraformResource::logicalId)
        .containsExactly("mystoreproductionevents", "cartreadmodel", "productreadmodel");
    assertThat(stack.terraformStack.elements(TerraformResource.class)).extracting(TerraformResource::logicalId)
        .contains("oductiondispatchedevents");
    assertThat(stack.webPubSub).isNull();
    assertThat(stack.webPubSubHub).isNull();
  }

  @Test
  void writes_nan_throughputs_as_null_like_parse_int() {
    EnvironmentVariables.set("AZURE_MAX_CONTAINER_THROUGHPUT", "lots");
    EnvironmentVariables.set("AZURE_MAX_DATABASE_THROUGHPUT", "4000RU");
    ApplicationSynthStack stack = synth(Fixtures.config()).synth(null);
    assertThat(stack.cosmosdbSqlDatabase.attribute("autoscale_settings")).isEqualTo(Map.of("max_throughput", 4000.0));
    assertThat(stack.terraformStack.toJson()).contains("\"max_throughput\": null").contains("\"max_throughput\": 4000");
  }

  @Test
  void uses_the_configured_java_version() {
    EnvironmentVariables.set("BOOSTER_AZURE_JAVA_VERSION", "25");
    ApplicationSynthStack stack = synth(Fixtures.config()).synth(null);
    assertThat(stack.functionApp.attribute("site_config"))
        .isEqualTo(Map.of("application_stack", Map.of("java_version", "25")));
  }

  @Test
  void the_resource_group_needs_the_region() {
    EnvironmentVariables.set("REGION", "");
    assertThatThrownBy(() -> synth(Fixtures.config()).synth(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("REGION was not properly loaded");
  }

  @Test
  void the_builders_check_their_prerequisites() {
    ApplicationSynthStack stack = emptyStack();
    BoosterConfig config = Fixtures.config();
    assertUndefined(() -> TerraformVirtualNetwork.build(stack), "networkSecurityGroup");
    assertUndefined(() -> TerraformSubnet.build(stack), "networkSecurityGroup");
    assertUndefined(() -> TerraformSubnetSecurity.build(stack), "networkSecurityGroup");
    assertUndefined(() -> TerraformApplicationGateway.build(stack), "virtualNetwork");
    assertUndefined(() -> TerraformPublicIpData.build(stack), "publicIP");
    assertUndefined(() -> TerraformCosmosdbSqlDatabase.build(stack, config), "cosmosdbDatabase");
    assertUndefined(() -> TerraformContainers.build(stack, config), "cosmosdbDatabase");
    assertUndefined(() -> TerraformContainers.createDedupEventsContainer(stack, config), "cosmosdbDatabase");
    assertUndefined(() -> TerraformEventHub.build(stack, config), "eventHubNamespace");
    assertUndefined(() -> TerraformFunctionAppSettings.build(stack, config, stack.resourceGroup, "func"),
        "cosmosdbDatabase");
    assertUndefined(() -> TerraformFunctionApp.build(stack, null, null, "func", "app", null, null), "cosmosdbDatabase");
    assertUndefined(() -> TerraformWebPubsubHub.build(stack), "webPubSub");

    stack.networkSecurityGroup = stack.resourceGroup;
    assertUndefined(() -> TerraformSubnet.build(stack), "virtualNetwork");
    assertUndefined(() -> TerraformSubnetSecurity.build(stack), "subnet");
    stack.virtualNetwork = stack.resourceGroup;
    assertUndefined(() -> TerraformApplicationGateway.build(stack), "subnet");
    stack.subnet = stack.resourceGroup;
    assertUndefined(() -> TerraformApplicationGateway.build(stack), "publicIP");
    stack.cosmosdbDatabase = stack.resourceGroup;
    assertUndefined(() -> TerraformContainers.build(stack, config), "cosmosdbSqlDatabase");
    assertUndefined(() -> TerraformFunctionApp.build(stack, null, null, "func", "app", null, null),
        "applicationServicePlan");
    stack.webPubSub = stack.resourceGroup;
    assertUndefined(() -> TerraformWebPubsubHub.build(stack), "functionApp");
  }

  @Test
  void the_app_settings_keep_config_env_positions_and_let_booster_settings_win() {
    BoosterConfig config = Fixtures.config();
    config.env.put("WEBSITE_RUN_FROM_PACKAGE", "0");
    config.env.put("BOOSTER_ENV", "overridden");
    EnvironmentVariables.set("BOOSTER_APP_NAME", "store-app");
    ApplicationSynth synth = synth(config);
    ApplicationSynthStack stack = synth.synth(null);
    Map<String, String> settings = synth.buildDefaultAppSettings(stack, stack.storageAccount, "func");
    assertThat(settings.keySet()).containsExactly("WEBSITE_RUN_FROM_PACKAGE", "WEBSITE_CONTENTSHARE", "CUSTOM_SETTING",
        "BOOSTER_ENV", "WebPubSubConnectionString", "BOOSTER_REST_API_URL", "EVENTHUB_CONNECTION_STRING",
        "EVENTHUB_NAME", "EVENTHUB_MAX_RETRIES", "EVENTHUB_MODE", "COSMOSDB_CONNECTION_STRING",
        "WEBSITE_CONTENTAZUREFILECONNECTIONSTRING", "BOOSTER_APP_NAME", "BOOSTER_APP_CLASS");
    assertThat(settings).containsEntry("WEBSITE_RUN_FROM_PACKAGE", "0")
        .containsEntry("BOOSTER_ENV", "production")
        .containsEntry("BOOSTER_APP_NAME", "store-app")
        .containsEntry("BOOSTER_REST_API_URL", "http://mystoreproductionrgapis.eastus.cloudapp.azure.com/production");
  }

  @Test
  void the_gateway_and_outputs_use_booster_env_with_azure_as_default() {
    EnvironmentVariables.set("BOOSTER_ENV", "staging");
    ApplicationSynthStack stack = synth(Fixtures.config()).synth(null);
    assertThat(stack.terraformStack.toJson()).contains("\"/staging/graphql/*\"")
        .contains("/staging/sensor/health(/.*)?")
        .contains("\"value\": \"http://${data.azurerm_public_ip.mystoreproductiondatapip.fqdn}/staging/graphql\"");
  }

  @Test
  void every_security_rule_attribute_is_rendered_because_terraform_json_requires_them_all() {
    ApplicationSynthStack stack = synth(Fixtures.config()).synth(null);
    List<?> rules = (List<?>) stack.networkSecurityGroup.attribute("security_rule");
    assertThat(rules).hasSize(2).allSatisfy(rule -> {
      // the azurerm schema of security_rule (an attribute of type list(object), not a block)
      assertThat(((Map<?, ?>) rule).keySet()).map(Object::toString).containsExactlyInAnyOrder("name", "priority", "direction", "access",
          "protocol", "source_port_range", "destination_port_range", "source_address_prefix",
          "destination_address_prefix", "description", "destination_address_prefixes",
          "destination_application_security_group_ids", "destination_port_ranges", "source_address_prefixes",
          "source_application_security_group_ids", "source_port_ranges");
      for (String unset : TerraformNetworkSecurityGroup.UNSET_RULE_ATTRIBUTES) {
        assertThat(((Map<?, ?>) rule).get(unset)).as(unset).isNull();
      }
    });
    assertThat(stack.terraformStack.toJson()).contains("\"description\": null")
        .contains("\"source_application_security_group_ids\": null");
  }
}
