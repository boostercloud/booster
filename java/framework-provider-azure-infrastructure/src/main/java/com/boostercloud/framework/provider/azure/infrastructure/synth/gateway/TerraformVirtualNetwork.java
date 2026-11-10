package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.List;

/** Port of {@code synth/gateway/terraform-virtual-network.ts}: the {@code azurerm_virtual_network} (10.0.0.0/16). */
public final class TerraformVirtualNetwork {

  private TerraformVirtualNetwork() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'vn')}, name {@code <rg>vn}.
   *
   * @param stack the stack bag
   * @return the resource
   * @throws IllegalStateException {@code Undefined networkSecurityGroup resource}
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    if (stack.networkSecurityGroup == null) {
      throw new IllegalStateException("Undefined networkSecurityGroup resource");
    }
    return new TerraformResource(stack.terraformStack, Utils.toTerraformName(stack.appPrefix, "vn"),
        "azurerm_virtual_network")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + "vn",
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "address_space", List.of("10.0.0.0/16")));
  }
}
