package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/**
 * Port of {@code synth/gateway/terraform-subnet-security.ts}: the
 * {@code azurerm_subnet_network_security_group_association}.
 */
public final class TerraformSubnetSecurity {

  private TerraformSubnetSecurity() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'snsg')}.
   *
   * @param stack the stack bag
   * @return the resource
   * @throws IllegalStateException {@code Undefined networkSecurityGroup resource} / {@code Undefined subnet resource}
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    if (stack.networkSecurityGroup == null) {
      throw new IllegalStateException("Undefined networkSecurityGroup resource");
    }
    if (stack.subnet == null) {
      throw new IllegalStateException("Undefined subnet resource");
    }
    return new TerraformResource(stack.terraformStack, Utils.toTerraformName(stack.appPrefix, "snsg"),
        "azurerm_subnet_network_security_group_association")
        .setAttributes(Attributes.of(
            "subnet_id", stack.subnet.ref("id"),
            "network_security_group_id", stack.networkSecurityGroup.ref("id")));
  }
}
