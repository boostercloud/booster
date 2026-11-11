package com.boostercloud.framework.provider.azure.infrastructure.synth.gateway;

import com.boostercloud.framework.provider.azure.infrastructure.synth.Attributes;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code synth/gateway/terraform-network-security-group.ts}: the {@code azurerm_network_security_group} of the
 * gateway subnet, allowing all TCP traffic in both directions (no explicit provider, as in TypeScript).
 *
 * <p>{@code security_rule} is an <em>attribute</em> of type {@code list(object)} in the azurerm schema (not a nested
 * block), and Terraform's JSON syntax requires every attribute of such an object to be present. The cdktn provider
 * bindings ({@code networkSecurityGroupSecurityRuleToTerraform}) therefore render every attribute of the rule, the ones
 * TypeScript does not set as {@code null}; so does this builder ({@link #UNSET_RULE_ATTRIBUTES}). Without them
 * {@code terraform validate} rejects the stack ({@code Inappropriate value for attribute "security_rule": ...
 * attributes "description", ... are required}).
 */
public final class TerraformNetworkSecurityGroup {

  /** The attributes of a {@code security_rule} object the TypeScript rules leave unset, rendered as {@code null}. */
  public static final List<String> UNSET_RULE_ATTRIBUTES = List.of("description", "destination_address_prefixes",
      "destination_application_security_group_ids", "destination_port_ranges", "source_address_prefixes",
      "source_application_security_group_ids", "source_port_ranges");

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
    Map<String, Object> rule = new LinkedHashMap<>(Attributes.of(
        "name", name,
        "priority", 100,
        "direction", direction,
        "access", "Allow",
        "protocol", "Tcp",
        "source_port_range", "*",
        "destination_port_range", "*",
        "source_address_prefix", "*",
        "destination_address_prefix", "*"));
    for (String attribute : UNSET_RULE_ATTRIBUTES) {
      rule.put(attribute, null);
    }
    return rule;
  }
}
