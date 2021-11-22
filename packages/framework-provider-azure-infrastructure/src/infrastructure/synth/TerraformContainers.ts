import { TerraformStack } from 'cdktf'
import { CosmosdbAccount, CosmosdbSqlContainer, CosmosdbSqlDatabase, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { eventsStoreAttributes } from '@boostercloud/framework-provider-azure'

export class TerraformContainers {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    appPrefix: string,
    cosmosdbDatabase: CosmosdbAccount,
    cosmosdbSqlDatabase: CosmosdbSqlDatabase,
    config: BoosterConfig
  ): Array<CosmosdbSqlContainer> {
    const idEvent = toTerraformName(appPrefix, 'events')
    const cosmosdbSqlEventContainer = new CosmosdbSqlContainer(terraformStack, idEvent, {
      name: config.resourceNames.eventsStore,
      resourceGroupName: cosmosdbDatabase.resourceGroupName,
      accountName: cosmosdbDatabase.name,
      databaseName: cosmosdbSqlDatabase.name,
      partitionKeyPath: `/${eventsStoreAttributes.partitionKey}`,
      partitionKeyVersion: 2,
      throughput: 400,
    })

    const readModels = Object.keys(config.readModels).map((readModel) =>
      this.createReadModel(terraformStack, appPrefix, config, readModel, cosmosdbDatabase, cosmosdbSqlDatabase)
    )

    return [cosmosdbSqlEventContainer].concat(readModels)
  }

  private static createReadModel(
    terraformStack: TerraformStack,
    appPrefix: string,
    config: BoosterConfig,
    readModel: string,
    cosmosdbDatabase: CosmosdbAccount,
    cosmosdbSqlDatabase: CosmosdbSqlDatabase
  ): CosmosdbSqlContainer {
    const readModelName = config.resourceNames.forReadModel(readModel)
    const idReadModel = toTerraformName(appPrefix, readModelName.substr(0, 23))
    return new CosmosdbSqlContainer(terraformStack, idReadModel, {
      name: readModelName,
      resourceGroupName: cosmosdbDatabase.resourceGroupName,
      accountName: cosmosdbDatabase.name,
      databaseName: cosmosdbSqlDatabase.name,
      partitionKeyPath: '/id',
      partitionKeyVersion: 2,
      throughput: 400,
    })
  }
}
