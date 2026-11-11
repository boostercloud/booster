package com.boostercloud.rocket.fileuploads.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.Constants;

/**
 * Port of {@code synth/terraform-storage-account.ts}.
 */
public final class TerraformStorageAccount {

  private TerraformStorageAccount() {}

  /**
   * {@code TerraformStorageAccount.build(...)}: an {@code azurerm_storage_account} (Standard, LRS) in the application's
   * resource group, construct id {@code toTerraformName(appPrefix, <storageName>rfst)}, named
   * {@code azureStorageName(appName, environmentName, storageName)}.
   *
   * @param stack the stack bag
   * @param utils the rocket utilities
   * @param config the application configuration
   * @param storageAccountNameParameter the configured storage name
   * @return the resource
   * @throws IllegalArgumentException {@code Error. Suffix could not be greater than 24} for a storage name longer than
   *     20 characters (without {@code -} and {@code _})
   */
  public static TerraformResource build(ApplicationSynthStack stack, RocketUtils utils, BoosterConfig config,
      String storageAccountNameParameter) {
    String id = utils.toTerraformName(stack.appPrefix, jsString(storageAccountNameParameter) + "rfst");
    String storageAccountName = Constants.azureStorageName(config.appName, config.environmentName,
        storageAccountNameParameter);
    return new TerraformResource(stack.terraformStack, id, "azurerm_storage_account")
        .setAttributes(Attributes.of(
            "name", storageAccountName,
            "resource_group_name", stack.resourceGroup.ref("name"),
            "location", stack.resourceGroup.ref("location"),
            "account_replication_type", "LRS",
            "account_tier", "Standard"))
        .provider(stack.azureProvider);
  }

  private static String jsString(String value) {
    return value == null ? "undefined" : value;
  }
}
