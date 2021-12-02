import { TerraformStack } from 'cdktf'
import { CosmosdbAccount, CosmosdbSqlDatabase, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'

export class TerraformCosmosdbSqlDatabase {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    appPrefix: string,
    cosmosdbAccount: CosmosdbAccount,
    config: BoosterConfig
  ): CosmosdbSqlDatabase {
    const idDatabase = toTerraformName(appPrefix, 'dbd')
    return new CosmosdbSqlDatabase(terraformStack, idDatabase, {
      name: config.resourceNames.applicationStack,
      resourceGroupName: resourceGroup.name,
      accountName: cosmosdbAccount.name,
      autoscaleSettings: {
        maxThroughput: 10000,
      },
    })
  }
}
