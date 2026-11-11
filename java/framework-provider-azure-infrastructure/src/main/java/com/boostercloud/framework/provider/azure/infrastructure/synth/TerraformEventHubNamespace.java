package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/** Port of {@code synth/terraform-event-hub-namespace.ts}: the {@code azurerm_eventhub_namespace}. */
public final class TerraformEventHubNamespace {

  private TerraformEventHubNamespace() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'ehn')}, name {@code <rg>ehn}, Basic SKU, capacity 2, no
   * auto-inflate.
   *
   * @param stack the stack bag
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    String id = Utils.toTerraformName(stack.appPrefix, "ehn");
    return new TerraformResource(stack.terraformStack, id, "azurerm_eventhub_namespace")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + "ehn",
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            // Basic, Standard and Premium. Maximum size of Event Hubs publication: Basic=256K, Standard=1Mb
            "sku", "Basic",
            // Throughput Units for a Standard SKU namespace
            "capacity", 2,
            "auto_inflate_enabled", false))
        .provider(stack.azureProvider);
  }
}
