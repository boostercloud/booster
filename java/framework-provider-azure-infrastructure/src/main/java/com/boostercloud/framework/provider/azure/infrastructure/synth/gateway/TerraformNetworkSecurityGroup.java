package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code synth/gateway/terraform-network-security-group.ts}: the {@code azurerm_network_security_group} of the
 * gateway subnet, allowing all TCP traffic in both directions (no explicit provider, as in TypeScript).
 */
public final class TerraformNetworkSecurityGroup {

  private TerraformNetworkSecurityGroup() {}

  /**
   * @param stack the stack bag
   * @param idSG the construct id ({@code toTerraformName(appPrefix, 'sg')})
   * @param name the name ({@code <rg>rjsg})
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack, String idSG, String name) {
    return new TerraformResource(stack.terraformStack, idSG, "azurerm_network_security_group")
        .setAttributes(Attributes.of(
            "name", name,
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "security_rule", List.of(rule("Allow-All-" + name, "Inbound"), rule("Allow-All-" + name + "b", "Outbound"))));
  }

  private static Map<String, Object> rule(String name, String direction) {
    return Attributes.of(
        "name", name,
        "priority", 100,
        "direction", direction,
        "access", "Allow",
        "protocol", "Tcp",
        "source_port_range", "*",
        "destination_port_range", "*",
        "source_address_prefix", "*",
        "destination_address_prefix", "*");
  }
}
