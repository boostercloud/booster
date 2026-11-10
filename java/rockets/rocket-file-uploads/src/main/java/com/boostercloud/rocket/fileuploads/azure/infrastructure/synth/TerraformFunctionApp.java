package com.boostercloud.rocket.fileuploads.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.Rockets;
import com.boostercloud.rocket.fileuploads.azure.infrastructure.Helper;
import com.boostercloud.rocket.fileuploads.types.RocketFilesParams;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Port of {@code synth/terraform-function-app.ts}: the rocket's function app.
 */
public final class TerraformFunctionApp {

  /** The Java-only app setting naming the application class the Java worker boots. */
  public static final String BOOSTER_APP_CLASS = "BOOSTER_APP_CLASS";

  private TerraformFunctionApp() {}

  /**
   * {@code TerraformFunctionApp.build(...)}: an {@code azurerm_windows_function_app} named {@code <rg>filesfunc} on the
   * application's service plan and host storage account, construct id {@code toTerraformName(appPrefix, 'rffunc')},
   * Functions runtime {@code ~4}, a system-assigned identity, and the app settings, in TypeScript's order (later keys
   * win): {@code WEBSITE_RUN_FROM_PACKAGE=1}, {@code WEBSITE_CONTENTSHARE=<id>}, every {@code config.env} entry,
   * {@code BOOSTER_ENV}, {@code COSMOSDB_CONNECTION_STRING}, {@code BOOSTER_ROCKET_FUNCTION_ID=rocket-files}, the
   * Java-only {@code BOOSTER_APP_CLASS}, then the identity-based connections of the storage accounts
   * ({@code <account>__serviceUri}). The site configuration is the Java worker's
   * ({@code TerraformFunctionApp.siteConfig()} of the Azure infrastructure) instead of {@code nodeVersion: '~22'}.
   *
   * @param stack the stack bag
   * @param config the application configuration
   * @param utils the rocket utilities
   * @param accountConnections the {@code <account>__serviceUri} settings
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack, BoosterConfig config, RocketUtils utils,
      Map<String, String> accountConnections) {
    TerraformResource resourceGroup = stack.resourceGroup;
    TerraformResource applicationServicePlan = Objects.requireNonNull(stack.applicationServicePlan,
        "Undefined applicationServicePlan resource");
    TerraformResource storageAccount = Objects.requireNonNull(stack.storageAccount,
        "Undefined storageAccount resource");
    String cosmosDatabaseName = stack.cosmosdbDatabase == null ? "undefined" : stack.cosmosdbDatabase.ref("name");
    String cosmosDbConnectionString = stack.cosmosdbDatabase == null ? "undefined"
        : stack.cosmosdbDatabase.ref("primary_key");
    String functionAppName = Helper.getFunctionAppName(stack);
    String id = utils.toTerraformName(stack.appPrefix, "rffunc");

    Map<String, Object> appSettings = new LinkedHashMap<>();
    appSettings.put("WEBSITE_RUN_FROM_PACKAGE", "1");
    appSettings.put("WEBSITE_CONTENTSHARE", id);
    if (config.env != null) {
      appSettings.putAll(config.env);
    }
    appSettings.put("BOOSTER_ENV", config.environmentName);
    appSettings.put("COSMOSDB_CONNECTION_STRING", "AccountEndpoint=https://" + cosmosDatabaseName
        + ".documents.azure.com:443/;AccountKey=" + cosmosDbConnectionString + ";");
    appSettings.put(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, RocketFilesParams.FUNCTION_ID);
    appSettings.put(BOOSTER_APP_CLASS, stack.boosterAppClass);
    appSettings.putAll(accountConnections);

    return new TerraformResource(stack.terraformStack, id, "azurerm_windows_function_app")
        .setAttributes(Attributes.of(
            "name", functionAppName,
            "location", resourceGroup.ref("location"),
            "resource_group_name", resourceGroup.ref("name"),
            "service_plan_id", applicationServicePlan.ref("id"),
            "app_settings", appSettings,
            "storage_account_name", storageAccount.ref("name"),
            "storage_account_access_key", storageAccount.ref("primary_access_key"),
            "site_config", com.boostercloud.framework.provider.azure.infrastructure.synth.TerraformFunctionApp
                .siteConfig(),
            "functions_extension_version", "~4",
            "identity", Attributes.of("type", "SystemAssigned")))
        .dependsOn(resourceGroup)
        .ignoreChanges(com.boostercloud.framework.provider.azure.infrastructure.synth.TerraformFunctionApp
            .IGNORE_RUN_FROM_PACKAGE)
        .provider(stack.azureProvider);
  }
}
