package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.List;

/** Port of {@code synth/gateway/terraform-subnet.ts}: the gateway {@code azurerm_subnet} (10.0.1.0/24). */
public final class TerraformSubnet {

  private TerraformSubnet() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'vsn')}, name {@code <rg>vsn}.
   *
   * @param stack the stack bag
   * @return the resource
   * @throws IllegalStateException {@code Undefined networkSecurityGroup resource} / {@code Undefined virtualNetwork
   *     resource}
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    if (stack.networkSecurityGroup == null) {
      throw new IllegalStateException("Undefined networkSecurityGroup resource");
    }
    if (stack.virtualNetwork == null) {
      throw new IllegalStateException("Undefined virtualNetwork resource");
    }
    return new TerraformResource(stack.terraformStack, Utils.toTerraformName(stack.appPrefix, "vsn"), "azurerm_subnet")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + "vsn",
            "resource_group_name", stack.resourceGroupName,
            "virtual_network_name", stack.virtualNetwork.ref("name"),
            "address_prefixes", List.of("10.0.1.0/24")));
  }
}
