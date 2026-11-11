package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/** Port of {@code synth/terraform-web-pubsub.ts}: the {@code azurerm_web_pubsub} service. */
public final class TerraformWebPubsub {

  private TerraformWebPubsub() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'wps')}, name {@code <rg>wps}, {@code Free_F1}, capacity 1.
   *
   * @param stack the stack bag
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    String id = Utils.toTerraformName(stack.appPrefix, "wps");
    return new TerraformResource(stack.terraformStack, id, "azurerm_web_pubsub")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + "wps",
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "sku", "Free_F1",
            "capacity", 1))
        .provider(stack.azureProvider);
  }
}
