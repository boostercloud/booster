package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import java.util.List;

/** Port of {@code synth/terraform-cosmosdb-database.ts}: the {@code azurerm_cosmosdb_account}. */
public final class TerraformCosmosdbDatabase {

  private TerraformCosmosdbDatabase() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'dba')}, name {@code <rg>cdba}, session consistency, one geo
   * location (the resource group's), automatic failover, depends on the resource group.
   *
   * @param stack the stack bag
   * @return the resource
   */
  public static TerraformResource build(ApplicationSynthStack stack) {
    String idAccount = Utils.toTerraformName(stack.appPrefix, "dba");
    return new TerraformResource(stack.terraformStack, idAccount, "azurerm_cosmosdb_account")
        .setAttributes(Attributes.of(
            "name", stack.resourceGroupName + "cdba",
            "location", stack.resourceGroup.ref("location"),
            "resource_group_name", stack.resourceGroupName,
            "offer_type", "Standard",
            "kind", "GlobalDocumentDB",
            "multiple_write_locations_enabled", false,
            "is_virtual_network_filter_enabled", false,
            "automatic_failover_enabled", true,
            "geo_location", List.of(Attributes.of(
                "location", stack.resourceGroup.ref("location"),
                "failover_priority", 0)),
            "consistency_policy", Attributes.of("consistency_level", "Session")))
        .dependsOn(stack.resourceGroup)
        .provider(stack.azureProvider);
  }
}
