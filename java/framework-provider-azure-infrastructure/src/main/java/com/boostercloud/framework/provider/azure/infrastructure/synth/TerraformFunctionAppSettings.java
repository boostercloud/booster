package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.Constants.OtherEnvironmentVarNames;
import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code synth/terraform-function-app-settings.ts}: the app settings (environment variables) of the Booster
 * function apps.
 */
public final class TerraformFunctionAppSettings {

  private TerraformFunctionAppSettings() {}

  /**
   * The settings, in this order (later keys override earlier ones in place, so {@code config.env} can only override
   * the first two): {@code WEBSITE_RUN_FROM_PACKAGE}, {@code WEBSITE_CONTENTSHARE}, every {@code config.env} entry,
   * {@code WebPubSubConnectionString}, {@code BOOSTER_ENV}, {@code BOOSTER_REST_API_URL},
   * {@code EVENTHUB_CONNECTION_STRING}, {@code EVENTHUB_NAME}, {@code EVENTHUB_MAX_RETRIES}, {@code EVENTHUB_MODE},
   * {@code COSMOSDB_CONNECTION_STRING}, {@code WEBSITE_CONTENTAZUREFILECONNECTIONSTRING}, {@code BOOSTER_APP_NAME}
   * and, Java only, {@code BOOSTER_APP_CLASS}. Values that depend on other resources are references.
   *
   * @param stack the stack bag (what exists at call time decides the Web PubSub and Event Hub settings)
   * @param config the configuration
   * @param storageAccount the host storage account of the function app
   * @param suffixName {@code func} or {@code fhub} (the {@code WEBSITE_CONTENTSHARE} suffix)
   * @return the settings
   * @throws IllegalStateException {@code Undefined cosmosdbDatabase resource}
   */
  public static Map<String, String> build(
      ApplicationSynthStack stack, BoosterConfig config, TerraformResource storageAccount, String suffixName) {
    TerraformResource cosmosdbDatabase = stack.cosmosdbDatabase;
    if (cosmosdbDatabase == null) {
      throw new IllegalStateException("Undefined cosmosdbDatabase resource");
    }
    String id = Utils.toTerraformName(stack.appPrefix, suffixName);
    String eventHubConnectionString = stack.eventHubNamespace != null && stack.eventHub != null
        ? stack.eventHubNamespace.ref("default_primary_connection_string") + ";EntityPath=" + stack.eventHub.ref("name")
        : "";
    String region = Utils.urlRegion();
    EventStreamConfiguration.Parameters parameters = config.eventStreamConfiguration.parameters();
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("WEBSITE_RUN_FROM_PACKAGE", "1");
    settings.put("WEBSITE_CONTENTSHARE", id);
    settings.putAll(config.env);
    settings.put(OtherEnvironmentVarNames.WEB_PUB_SUB_CONNECTION_STRING,
        stack.webPubSub != null ? stack.webPubSub.ref("primary_connection_string") : "");
    settings.put("BOOSTER_ENV", config.environmentName);
    settings.put(EnvironmentVarNames.REST_API_URL,
        "http://" + stack.domainNameLabel + "." + region + ".cloudapp.azure.com/" + config.environmentName);
    settings.put(EnvironmentVarNames.EVENT_HUB_CONNECTION_STRING, eventHubConnectionString);
    settings.put(EnvironmentVarNames.EVENT_HUB_NAME, config.resourceNames().streamTopic());
    settings.put(EnvironmentVarNames.EVENT_HUB_MAX_RETRIES,
        parameters != null && parameters.maxRetries() != null ? parameters.maxRetries().toString() : "5");
    settings.put(EnvironmentVarNames.EVENT_HUB_MODE,
        parameters != null && parameters.mode() != null ? parameters.mode().value() : "exponential");
    settings.put(EnvironmentVarNames.COSMOS_DB_CONNECTION_STRING,
        "AccountEndpoint=https://" + cosmosdbDatabase.ref("name") + ".documents.azure.com:443/;AccountKey="
            + cosmosdbDatabase.ref("primary_key") + ";");
    // Terraform bug: https://github.com/hashicorp/terraform-provider-azurerm/issues/16650
    settings.put("WEBSITE_CONTENTAZUREFILECONNECTIONSTRING", storageAccount.ref("primary_connection_string"));
    settings.put(Constants.BOOSTER_APP_NAME, EnvironmentVariables.get(Constants.BOOSTER_APP_NAME).orElse(""));
    settings.put(OtherEnvironmentVarNames.BOOSTER_APP_CLASS, stack.boosterAppClass);
    return settings;
  }
}
