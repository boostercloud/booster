package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/**
 * Port of {@code synth/gateway/terraform-public-ip.ts}: the static, Standard {@code azurerm_public_ip} of the gateway,
 * with the domain name label {@code <rg>apis}.
 */
public final class TerraformPublicIp {

  private TerraformPublicIp() {}

  /**
   * @param stack the stack bag
   * @param idPIP the construct id ({@code pip}, not prefixed)
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack, String idPIP) {
    return new TerraformResource(stack.terraformStack, idPIP, "azurerm_public_ip")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + "pip",
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "allocation_method", "Static",
            "sku", "Standard",
            "domain_name_label", stack.domainNameLabel));
  }
}
