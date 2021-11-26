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
    const cosmosdbSqlEventContainer = this.createEventContainer(
      appPrefix,
      terraformStack,
      config,
      cosmosdbDatabase,
      cosmosdbSqlDatabase
    )
    const readModels = Object.keys(config.readModels).map((readModel) =>
      this.createReadModel(terraformStack, config, readModel, cosmosdbDatabase, cosmosdbSqlDatabase)
    )
    return [cosmosdbSqlEventContainer].concat(readModels)
  }

  private static createEventContainer(
    appPrefix: string,
    terraformStack: TerraformStack,
    config: BoosterConfig,
    cosmosdbDatabase: CosmosdbAccount,
    cosmosdbSqlDatabase: CosmosdbSqlDatabase
  ): CosmosdbSqlContainer {
    const idEvent = toTerraformName(appPrefix, 'events')
    return new CosmosdbSqlContainer(terraformStack, idEvent, {
      name: config.resourceNames.eventsStore,
      resourceGroupName: cosmosdbDatabase.resourceGroupName,
      accountName: cosmosdbDatabase.name,
      databaseName: cosmosdbSqlDatabase.name,
      partitionKeyPath: `/${eventsStoreAttributes.partitionKey}`,
      partitionKeyVersion: 2,
      autoscaleSettings: {
        maxThroughput: 10000,
      },
    })
  }

  private static createReadModel(
    terraformStack: TerraformStack,
    config: BoosterConfig,
    readModel: string,
    cosmosdbDatabase: CosmosdbAccount,
    cosmosdbSqlDatabase: CosmosdbSqlDatabase
  ): CosmosdbSqlContainer {
    const readModelName = config.resourceNames.forReadModel(readModel)
    const idReadModel = toTerraformName(readModel)
    return new CosmosdbSqlContainer(terraformStack, idReadModel, {
      name: readModelName,
      resourceGroupName: cosmosdbDatabase.resourceGroupName,
      accountName: cosmosdbDatabase.name,
      databaseName: cosmosdbSqlDatabase.name,
      partitionKeyPath: '/id',
      partitionKeyVersion: 2,
      autoscaleSettings: {
        maxThroughput: 10000,
      },
    })
  }
}
