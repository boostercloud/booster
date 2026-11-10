package com.boostercloud.rocket.fileuploads.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/**
 * Port of {@code synth/terraform-storage-container.ts}.
 */
public final class TerraformStorageContainer {

  private TerraformStorageContainer() {}

  /**
   * {@code TerraformStorageContainer.build(...)}: a private {@code azurerm_storage_container} in the rocket's storage
   * account, construct id {@code toTerraformName(<storage account name>, rfsb<containerName>)} (TypeScript passes the
   * unresolved name token; Java the name, which gives a deterministic id).
   *
   * @param stack the stack bag
   * @param rocketStorageAccount the storage account
   * @param storageAccountName its name
   * @param containerName the container
   * @param utils the rocket utilities
   * @return the resource
   * @throws IllegalArgumentException {@code Error. Suffix could not be greater than 24} for a container name longer
   *     than 20 characters (without {@code -} and {@code _})
   */
  public static TerraformResource build(ApplicationSynthStack stack, TerraformResource rocketStorageAccount,
      String storageAccountName, String containerName, RocketUtils utils) {
    String id = utils.toTerraformName(storageAccountName, "rfsb" + (containerName == null ? "undefined" : containerName));
    return new TerraformResource(stack.terraformStack, id, "azurerm_storage_container")
        .setAttributes(Attributes.of(
            "name", containerName,
            "storage_account_id", rocketStorageAccount.ref("id"),
            "container_access_type", "private"))
        .provider(stack.azureProvider);
  }
}
