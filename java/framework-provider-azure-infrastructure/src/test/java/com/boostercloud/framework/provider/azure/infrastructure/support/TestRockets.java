package com.boostercloud.framework.provider.azure.infrastructure.support;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionAppLayout;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.synth.TerraformFunctionApp;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppFunctionsDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rockets shaped like the webhook and file-uploads rockets, for the synthesis and flow tests. */
public final class TestRockets {

  private TestRockets() {}

  /**
   * @return descriptors of the two rockets: the webhook one has list parameters, the files one a
   *     {@code rocketProviderPackage}
   */
  public static List<RocketDescriptor> descriptors() {
    Map<String, Object> filesParameters = new LinkedHashMap<>();
    filesParameters.put("rocketProviderPackage", "@boostercloud/rocket-file-uploads-azure");
    return List.of(
        new RocketDescriptor("com.example.rockets.webhook", List.of(Map.of("route", "test"))),
        new RocketDescriptor("com.example.rockets.files", filesParameters));
  }

  /** Like the webhook rocket: its own function app, gateway overrides and {@code mountFunctionsV4}. */
  public static final class WebhookRocket implements InfrastructureRocket {
    /** Calls of {@link #unmountStack()}. */
    public int unmounted;

    @Override
    public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      String id = utils.toTerraformName(stack.appPrefix, "webhookfunc");
      Map<String, Object> appSettings = new LinkedHashMap<>();
      appSettings.put("WEBSITE_RUN_FROM_PACKAGE", "1");
      appSettings.put("WEBSITE_CONTENTSHARE", id);
      appSettings.put("BOOSTER_ENV", config.environmentName);
      appSettings.put("COSMOSDB_CONNECTION_STRING", "AccountEndpoint=https://" + stack.cosmosdbDatabase.ref("name")
          + ".documents.azure.com:443/;AccountKey=" + stack.cosmosdbDatabase.ref("primary_key") + ";");
      appSettings.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook");
      appSettings.put("BOOSTER_APP_CLASS", stack.boosterAppClass);
      TerraformResource functionApp = new TerraformResource(stack.terraformStack, id, "azurerm_windows_function_app")
          .setAttributes(Attributes.of(
              "name", getFunctionAppName(stack),
              "location", stack.resourceGroup.ref("location"),
              "resource_group_name", stack.resourceGroup.ref("name"),
              "service_plan_id", stack.applicationServicePlan.ref("id"),
              "app_settings", appSettings,
              "storage_account_name", stack.storageAccount.ref("name"),
              "storage_account_access_key", stack.storageAccount.ref("primary_access_key"),
              "site_config", TerraformFunctionApp.siteConfig(),
              "functions_extension_version", "~4"))
          .dependsOn(stack.resourceGroup)
          .ignoreChanges(TerraformFunctionApp.IGNORE_RUN_FROM_PACKAGE)
          .provider(stack.azureProvider);
      List<TerraformResource> rocketStack = stack.rocketStack != null ? stack.rocketStack : new ArrayList<>();
      rocketStack.add(functionApp);
      stack.appGateway.addOverride("backend_address_pool", List.of(Attributes.of(
          "name", "rocketWebHookBackendAddressPool",
          "fqdns", List.of(functionApp.ref("name") + ".azurewebsites.net"))));
      stack.appGateway.addOverride("url_path_map.0.path_rule", List.of(Attributes.of(
          "name", utils.toTerraformName(stack.appPrefix, "rutest"),
          "paths", List.of("/test", "/test/*"),
          "backend_address_pool_name", "rocketWebHookBackendAddressPool",
          "backend_http_settings_name", "mainBackendHttpSettings",
          "rewrite_rule_set_name", "mainRewriteRuleSet")));
      return stack;
    }

    @Override
    public String getFunctionAppName(ApplicationSynthStack stack) {
      return stack.resourceGroupName + "webhookfunc";
    }

    @Override
    public List<FunctionAppV4Definition> mountFunctionsV4(
        BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      FunctionDefinition test = new FunctionDefinition("test", new FunctionConfig(
          List.of(Binding.httpTrigger("request", List.of("POST", "GET"), "anonymous", "test"), Binding.httpReturn()),
          FunctionAppLayout.scriptFile(config), BoosterAzureFunctions.ROCKET_HTTP_ENTRY_POINT));
      return List.of(FunctionAppV4Definition.of(getFunctionAppName(stack), List.of(test)));
    }

    @Override
    public void unmountStack() {
      unmounted++;
    }
  }

  /** Like the file-uploads rocket, but with the legacy {@code mountCode} hook. */
  public static final class FilesRocket implements InfrastructureRocket {
    @Override
    public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      stack.functionApp.addOverride("identity", Map.of("type", "SystemAssigned"));
      return stack;
    }

    @Override
    public String getFunctionAppName(ApplicationSynthStack stack) {
      return stack.resourceGroupName + "filesfunc";
    }

    @Override
    public List<FunctionAppFunctionsDefinition> mountCode(
        BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      FunctionDefinition upload = new FunctionDefinition("fileupload_clientst", new FunctionConfig(
          List.of(Binding.blobTrigger("blob", "rocketfiles/{name}", "clientst")),
          FunctionAppLayout.scriptFile(config), BoosterAzureFunctions.ROCKET_BLOB_ENTRY_POINT));
      return List.of(FunctionAppFunctionsDefinition.of(getFunctionAppName(stack), List.of(upload)));
    }
  }
}
