package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.Constants;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;

/** Port of {@code synth/terraform-cosmosdb-sql-database.ts}: the {@code azurerm_cosmosdb_sql_database}. */
public final class TerraformCosmosdbSqlDatabase {

  private TerraformCosmosdbSqlDatabase() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'dbd')}, name {@code config.resourceNames.applicationStack}
   * ({@code <app>-app}), autoscale up to {@code AZURE_MAX_DATABASE_THROUGHPUT}.
   *
   * @param stack the stack bag
   * @param config the configuration
   * @return the resource
   * @throws IllegalStateException {@code Undefined cosmosdbDatabase resource}
   */
  public static TerraformResource build(ApplicationSynthStack stack, BoosterConfig config) {
    if (stack.cosmosdbDatabase == null) {
      throw new IllegalStateException("Undefined cosmosdbDatabase resource");
    }
    String idDatabase = Utils.toTerraformName(stack.appPrefix, "dbd");
    return new TerraformResource(stack.terraformStack, idDatabase, "azurerm_cosmosdb_sql_database")
        .setAttributes(Attributes.of(
            "name", config.resourceNames().applicationStack(),
            "resource_group_name", stack.resourceGroupName,
            "account_name", stack.cosmosdbDatabase.ref("name"),
            "autoscale_settings", Attributes.of("max_throughput", Constants.maxDatabaseThroughput())))
        .provider(stack.azureProvider);
  }
}
