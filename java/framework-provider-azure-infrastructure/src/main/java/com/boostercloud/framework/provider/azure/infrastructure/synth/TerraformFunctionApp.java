package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code synth/terraform-function-app.ts}: an {@code azurerm_windows_function_app} running the Functions
 * runtime {@code ~4}. The Java port runs the Java worker ({@code site_config.application_stack.java_version}, see
 * {@link Constants#javaVersion()}) instead of Node ({@code nodeVersion: '~22'}).
 */
public final class TerraformFunctionApp {

  /** The lifecycle rule of every function app: Kudu deployments change {@code WEBSITE_RUN_FROM_PACKAGE}. */
  public static final String IGNORE_RUN_FROM_PACKAGE = "app_settings[\"WEBSITE_RUN_FROM_PACKAGE\"]";

  private TerraformFunctionApp() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, suffixName)}.
   *
   * @param stack the stack bag
   * @param applicationServicePlan the service plan
   * @param storageAccount the host storage account
   * @param suffixName {@code func} (main) or {@code fhub} (consumer)
   * @param functionAppName the function app name
   * @param zipFile the package Terraform deploys ({@code zip_deploy_file}), or {@code null}
   * @param appSettings the app settings, or {@code null} for {@code {}}
   * @return the resource
   * @throws IllegalStateException {@code Undefined cosmosdbDatabase resource} / {@code Undefined applicationServicePlan
   *     resource}
   */
  public static TerraformResource build(
      ApplicationSynthStack stack,
      TerraformResource applicationServicePlan,
      TerraformResource storageAccount,
      String suffixName,
      String functionAppName,
      String zipFile,
      Map<String, String> appSettings) {
    if (stack.cosmosdbDatabase == null) {
      throw new IllegalStateException("Undefined cosmosdbDatabase resource");
    }
    if (applicationServicePlan == null) {
      throw new IllegalStateException("Undefined applicationServicePlan resource");
    }
    String id = Utils.toTerraformName(stack.appPrefix, suffixName);
    return new TerraformResource(stack.terraformStack, id, "azurerm_windows_function_app")
        .setAttributes(Attributes.of(
            "name", functionAppName,
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "service_plan_id", applicationServicePlan.ref("id"),
            "app_settings", appSettings == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(appSettings),
            "storage_account_name", storageAccount.ref("name"),
            "storage_account_access_key", storageAccount.ref("primary_access_key"),
            "site_config", siteConfig(),
            "functions_extension_version", "~4",
            "zip_deploy_file", zipFile))
        .dependsOn(stack.resourceGroup)
        .ignoreChanges(IGNORE_RUN_FROM_PACKAGE)
        .provider(stack.azureProvider);
  }

  /**
   * @return {@code { application_stack: { java_version } }}, the site configuration of every Booster function app
   *     (rockets creating function apps use it too)
   */
  public static Map<String, Object> siteConfig() {
    return Attributes.of("application_stack", Attributes.of("java_version", Constants.javaVersion()));
  }
}
