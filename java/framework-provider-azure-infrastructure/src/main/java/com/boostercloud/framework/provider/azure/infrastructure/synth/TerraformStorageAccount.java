package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/** Port of {@code synth/terraform-storage-account.ts}: an {@code azurerm_storage_account} (Standard, LRS). */
public final class TerraformStorageAccount {

  private TerraformStorageAccount() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, suffixName)}, name {@code <rg><suffixName>}.
   *
   * @param stack the stack bag
   * @param suffixName {@code sp} (main) or {@code sc} (consumer)
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack, String suffixName) {
    String id = Utils.toTerraformName(stack.appPrefix, suffixName);
    return new TerraformResource(stack.terraformStack, id, "azurerm_storage_account")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + suffixName,
            "resource_group_name", stack.resourceGroupName,
            "location", stack.resourceGroup.ref("location"),
            "account_replication_type", "LRS",
            "account_tier", "Standard"))
        .provider(stack.azureProvider);
  }
}
