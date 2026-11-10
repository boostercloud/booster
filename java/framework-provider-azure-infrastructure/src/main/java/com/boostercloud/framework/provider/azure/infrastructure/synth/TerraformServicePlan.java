package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;

/** Port of {@code synth/terraform-service-plan.ts}: an {@code azurerm_service_plan} (Windows). */
public final class TerraformServicePlan {

  private TerraformServicePlan() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, suffixName)}, name {@code <rg><suffixName>}.
   *
   * @param stack the stack bag
   * @param suffixName {@code psp} (main) or {@code psc} (consumer)
   * @param skuName {@code Y1} or {@code B1}
   * @param workerCount the number of workers
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack, String suffixName, String skuName, int workerCount) {
    String id = Utils.toTerraformName(stack.appPrefix, suffixName);
    return new TerraformResource(stack.terraformStack, id, "azurerm_service_plan")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + suffixName,
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "os_type", "Windows",
            "sku_name", skuName,
            "worker_count", workerCount))
        .provider(stack.azureProvider);
  }
}
