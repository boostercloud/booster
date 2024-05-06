import { cosmosdbSqlDatabase } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'

import { MAX_DATABASE_THROUGHPUT } from '../constants'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformCosmosdbSqlDatabase {
  static build(
    { terraformStack, azureProvider, appPrefix, resourceGroupName, cosmosdbDatabase }: ApplicationSynthStack,
    config: BoosterConfig
  ): cosmosdbSqlDatabase.CosmosdbSqlDatabase {
    if (!cosmosdbDatabase) {
      throw new Error('Undefined cosmosdbDatabase resource')
    }
    const idDatabase = toTerraformName(appPrefix, 'dbd')
    return new cosmosdbSqlDatabase.CosmosdbSqlDatabase(terraformStack, idDatabase, {
      name: config.resourceNames.applicationStack,
      resourceGroupName: resourceGroupName,
      accountName: cosmosdbDatabase.name,
      autoscaleSettings: {
        maxThroughput: MAX_DATABASE_THROUGHPUT,
      },
      provider: azureProvider,
    })
  }
}
