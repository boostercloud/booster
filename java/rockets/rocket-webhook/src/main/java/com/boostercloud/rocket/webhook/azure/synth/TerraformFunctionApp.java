package com.boostercloud.rocket.webhook.azure.synth;

import com.boostercloud.framework.provider.azure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.Rockets;
import com.boostercloud.rocket.webhook.azure.Helper;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code synth/terraform-function-app.ts}: the {@code azurerm_windows_function_app} of the webhooks. It
 * shares the main application's service plan and storage account and runs a copy of the application, identified as
 * the rocket function {@code rocket-webhook} by the {@code BOOSTER_ROCKET_FUNCTION_ID} app setting.
 */
public final class TerraformFunctionApp {

  /** The Terraform resource type. */
  public static final String RESOURCE_TYPE = "azurerm_windows_function_app";

  private TerraformFunctionApp() {}

  /**
   * {@code build(applicationSynthStack, config, utils)}: construct id {@code toTerraformName(appPrefix, 'webhookfunc')}
   * in {@code applicationSynthStack.terraformStack}.
   *
   * <p>App settings, later keys winning: {@code WEBSITE_RUN_FROM_PACKAGE=1}, {@code WEBSITE_CONTENTSHARE=<id>}, every
   * {@code config.env} entry, {@code BOOSTER_ENV}, {@code COSMOSDB_CONNECTION_STRING},
   * {@code WEBSITE_CONTENTAZUREFILECONNECTIONSTRING}, {@code BOOSTER_ROCKET_FUNCTION_ID=rocket-webhook} and the
   * Java-only {@code BOOSTER_APP_CLASS} (the application the entry points boot).
   *
   * @param applicationSynthStack the stack bag
   * @param config the configuration
   * @param utils the rocket utilities
   * @return the resource
   * @throws IllegalStateException {@code Cosmosdb database not found}, {@code Storage account not found} or
   *     {@code Application service plan not found} (checked in that order)
   */
  public static TerraformResource build(ApplicationSynthStack applicationSynthStack, BoosterConfig config,
      RocketUtils utils) {
    TerraformResource cosmosdbDatabase = applicationSynthStack.cosmosdbDatabase;
    TerraformResource storageAccount = applicationSynthStack.storageAccount;
    TerraformResource applicationServicePlan = applicationSynthStack.applicationServicePlan;
    if (cosmosdbDatabase == null) {
      throw new IllegalStateException("Cosmosdb database not found");
    }
    if (storageAccount == null) {
      throw new IllegalStateException("Storage account not found");
    }
    if (applicationServicePlan == null) {
      throw new IllegalStateException("Application service plan not found");
    }
    String cosmosDatabaseName = cosmosdbDatabase.ref("name");
    String cosmosDbConnectionString = cosmosdbDatabase.ref("primary_key");

    String id = utils.toTerraformName(applicationSynthStack.appPrefix, Helper.FUNCTION_APP_SUFFIX);
    Map<String, Object> appSettings = new LinkedHashMap<>();
    appSettings.put("WEBSITE_RUN_FROM_PACKAGE", "1");
    appSettings.put("WEBSITE_CONTENTSHARE", id);
    if (config.env != null) {
      appSettings.putAll(config.env);
    }
    appSettings.put("BOOSTER_ENV", config.environmentName);
    appSettings.put("COSMOSDB_CONNECTION_STRING", "AccountEndpoint=https://" + cosmosDatabaseName
        + ".documents.azure.com:443/;AccountKey=" + cosmosDbConnectionString + ";");
    appSettings.put("WEBSITE_CONTENTAZUREFILECONNECTIONSTRING", storageAccount.ref("primary_connection_string"));
    appSettings.put(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, WebhookParams.FUNCTION_ID);
    appSettings.put(Constants.OtherEnvironmentVarNames.BOOSTER_APP_CLASS, applicationSynthStack.boosterAppClass);

    return new TerraformResource(applicationSynthStack.terraformStack, id, RESOURCE_TYPE)
        .setAttributes(Attributes.of(
            "name", Helper.getFunctionAppName(applicationSynthStack.resourceGroupName),
            "location", applicationSynthStack.resourceGroup.ref("location"),
            "resource_group_name", applicationSynthStack.resourceGroup.ref("name"),
            "service_plan_id", applicationServicePlan.ref("id"),
            "app_settings", appSettings,
            "storage_account_name", storageAccount.ref("name"),
            "storage_account_access_key", storageAccount.ref("primary_access_key"),
            "site_config",
            com.boostercloud.framework.provider.azure.infrastructure.synth.TerraformFunctionApp.siteConfig(),
            "functions_extension_version", "~4"))
        .dependsOn(applicationSynthStack.resourceGroup)
        .ignoreChanges(com.boostercloud.framework.provider.azure.infrastructure.synth.TerraformFunctionApp
            .IGNORE_RUN_FROM_PACKAGE)
        .provider(applicationSynthStack.azureProvider);
  }
}
