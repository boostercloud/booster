package com.boostercloud.rocket.webhook.azure.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformApp;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformProvider;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.StackNames;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.webhook.azure.AzureWebhook;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code functions/functions.ts} and {@code functions/webhook-function.ts}: one function app for every webhook, one HTTP
 * function per route, the TypeScript function names and the empty-endpoint error.
 */
class FunctionsTest {

  static final class Handler {}

  @TempDir
  Path root;

  private ApplicationSynthStack stack(String resourceGroupName) {
    TerraformStack terraformStack = new TerraformStack(new TerraformApp(root), "teststack");
    TerraformProvider provider = new TerraformProvider(terraformStack, "azurerm", "azurerm", "hashicorp/azurerm",
        "~> 5.0");
    StackNames names = new StackNames("testprefix", terraformStack, provider, resourceGroupName,
        resourceGroupName + "func", resourceGroupName + "fhub", resourceGroupName + "apis", "hub", "booster",
        "com.example.Main");
    return new ApplicationSynthStack(names, new TerraformResource(terraformStack, "rg", "azurerm_resource_group"));
  }

  private static BoosterConfig config() {
    BoosterConfig config = new BoosterConfig("production");
    config.appName = "my-store";
    return config;
  }

  @Test
  void one_definition_for_the_webhook_function_app_with_one_function_per_route_in_order() {
    List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("test", Handler.class),
        WebhookParamsEvent.of("clients/other", Handler.class), WebhookParamsEvent.of("v1.hooks-x", Handler.class));

    List<FunctionAppV4Definition> definitions = Functions.mountFunctionsV4(params, config(), stack("xrg"), null);

    assertThat(definitions).singleElement().satisfies(definition -> {
      assertThat(definition.functionAppName()).isEqualTo("xrgwebhookfunc");
      assertThat(definition.functionsDefinitions()).extracting(FunctionDefinition::name)
          .containsExactly("test", "clients_other", "v1_hooks_x");
    });
    assertThat(Functions.getFunctionAppName(params, stack("xrg"))).isEqualTo("xrgwebhookfunc");
    assertThat(new AzureWebhook(params).getFunctionAppName(stack("myappdevrg"))).isEqualTo("myappdevrgwebhookfunc");
  }

  @Test
  void the_function_is_an_anonymous_get_and_post_trigger_on_the_route_bound_to_rocket_http() {
    FunctionDefinition definition = WebhookFunction.generateFunctionsCode(config(), "clients/other");

    assertThat(definition.name()).isEqualTo("clients_other");
    assertThat(definition.config().bindings()).hasSize(2);
    assertThat(definition.config().bindings().getFirst().properties()).isEqualTo(Map.of("type", "httpTrigger",
        "direction", "in", "name", "request", "methods", List.of("POST", "GET"), "authLevel", "anonymous",
        "route", "clients/other", "dataType", "binary"));
    assertThat(definition.config().bindings().get(1).properties())
        .isEqualTo(Map.of("type", "http", "direction", "out", "name", "$return"));
    assertThat(definition.config().scriptFile()).isEqualTo("../my-store.jar");
    assertThat(definition.config().entryPoint()).isEqualTo(BoosterAzureFunctions.ROCKET_HTTP_ENTRY_POINT);
  }

  /** Names computed with Node 24: {@code endpoint.replace(/[^a-zA-Z0-9_]/g, '_')}. */
  @Test
  void function_names_replace_every_utf16_unit_outside_the_name_characters_as_javascript() {
    assertThat(WebhookFunction.functionName("a/b")).isEqualTo(WebhookFunction.functionName("a_b")).isEqualTo("a_b");
    assertThat(WebhookFunction.functionName("ñandú/ü")).isEqualTo("_and___");
    assertThat(WebhookFunction.functionName("v1.hooks-x")).isEqualTo("v1_hooks_x");
    // A character outside the BMP is a surrogate pair: two underscores
    assertThat(WebhookFunction.functionName("a😀b")).isEqualTo("a__b");
  }

  @Test
  void colliding_function_names_fail_the_synthesis_with_the_node_worker_message() {
    List<WebhookParamsEvent> params = List.of(WebhookParamsEvent.of("a/b", Handler.class),
        WebhookParamsEvent.of("ok", Handler.class), WebhookParamsEvent.of("a_b", Handler.class));

    assertThatThrownBy(() -> Functions.mountFunctionsV4(params, config(), stack("xrg"), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("A function with name \"a_b\" has already been registered.");
  }

  @Test
  void an_empty_endpoint_and_a_webhook_without_route_fail() {
    assertThatThrownBy(() -> WebhookFunction.generateFunctionsCode(config(), ""))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Webhook endpoint must be a non-empty string");
    assertThatThrownBy(() -> WebhookFunction.generateFunctionsCode(config(), null))
        .hasMessage("Webhook endpoint must be a non-empty string");
    assertThatThrownBy(() -> Functions.mountFunctionsV4(List.of(WebhookParamsEvent.builder().build()), config(),
        stack("xrg"), null)).hasMessage("Undefined route parameter [object Object]");
  }

  @Test
  void no_webhooks_still_give_one_empty_function_app() {
    assertThat(Functions.mountFunctionsV4(List.of(), config(), stack("xrg"), null))
        .singleElement().satisfies(definition -> assertThat(definition.functionsDefinitions()).isEmpty());
  }
}
