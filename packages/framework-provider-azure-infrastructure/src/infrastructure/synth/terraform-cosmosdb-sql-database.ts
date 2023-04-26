import { TerraformStack } from 'cdktf'
import { cosmosdbAccount, cosmosdbSqlDatabase, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

import { MAX_DATABASE_THROUGHPUT } from '../constants'

export class TerraformCosmosdbSqlDatabase {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    appPrefix: string,
    cosmosdbAccountResource: cosmosdbAccount.CosmosdbAccount,
    config: BoosterConfig
  ): cosmosdbSqlDatabase.CosmosdbSqlDatabase {
    const idDatabase = toTerraformName(appPrefix, 'dbd')
    return new cosmosdbSqlDatabase.CosmosdbSqlDatabase(terraformStackResource, idDatabase, {
      name: config.resourceNames.applicationStack,
      resourceGroupName: resourceGroupResource.name,
      accountName: cosmosdbAccountResource.name,
      autoscaleSettings: {
        maxThroughput: MAX_DATABASE_THROUGHPUT,
      },
      provider: providerResource,
    })
  }
}
