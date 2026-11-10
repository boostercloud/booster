package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformDataSource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/**
 * Port of {@code synth/gateway/terraform-public-ip-data.ts}: the {@code data.azurerm_public_ip} whose {@code fqdn}
 * feeds the outputs.
 */
public final class TerraformPublicIpData {

  private TerraformPublicIpData() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'datapip')}.
   *
   * @param stack the stack bag
   * @return the data source
   * @throws IllegalStateException {@code Undefined publicIP resource}
   */
  public static TerraformDataSource build(ApplicationSynthStack stack) {
    if (stack.publicIP == null) {
      throw new IllegalStateException("Undefined publicIP resource");
    }
    TerraformDataSource data = new TerraformDataSource(stack.terraformStack,
        Utils.toTerraformName(stack.appPrefix, "datapip"), "azurerm_public_ip");
    data.setAttributes(Attributes.of(
        "name", stack.publicIP.ref("name"),
        "resource_group_name", stack.resourceGroupName));
    data.provider(stack.azureProvider);
    return data;
  }
}
