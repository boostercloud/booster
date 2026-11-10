package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.StackNames;

/** Port of {@code synth/terraform-resource-group.ts}: the {@code azurerm_resource_group}. */
public final class TerraformResourceGroup {

  private TerraformResourceGroup() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'rg')}; {@code name} = the resource group name, {@code location} =
   * {@code REGION}.
   *
   * @param names the stack names
   * @return the resource
   * @throws IllegalStateException when {@code REGION} is not set ({@link Utils#REGION_NOT_LOADED})
   */
  public static TerraformResource build(StackNames names) {
    String id = Utils.toTerraformName(names.appPrefix, "rg");
    return new TerraformResource(names.terraformStack, id, "azurerm_resource_group")
        .setAttributes(Attributes.of("name", names.resourceGroupName, "location", Utils.getDeployRegion()))
        .provider(names.azureProvider);
  }
}
