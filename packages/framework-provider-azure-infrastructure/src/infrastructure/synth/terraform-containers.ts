import { TerraformStack } from 'cdktf'
import { cosmosdbAccount, cosmosdbSqlContainer, cosmosdbSqlDatabase, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import {
  connectionsStoreAttributes,
  eventsStoreAttributes,
  subscriptionsStoreAttributes,
} from '@boostercloud/framework-provider-azure'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

import { MAX_CONTAINER_THROUGHPUT } from '../constants'

export class TerraformContainers {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    appPrefix: string,
    cosmosdbDatabaseResource: cosmosdbAccount.CosmosdbAccount,
    cosmosdbSqlDatabaseResource: cosmosdbSqlDatabase.CosmosdbSqlDatabase,
    config: BoosterConfig
  ): Array<cosmosdbSqlContainer.CosmosdbSqlContainer> {
    const cosmosdbSqlEventContainer = this.createEventContainer(
      providerResource,
      appPrefix,
      terraformStackResource,
      config,
      cosmosdbDatabaseResource,
      cosmosdbSqlDatabaseResource
    )
    const readModels = Object.keys(config.readModels).map((readModel) =>
      this.createReadModel(
        providerResource,
        terraformStackResource,
        config,
        readModel,
        cosmosdbDatabaseResource,
        cosmosdbSqlDatabaseResource
      )
    )
    if (config.enableSubscriptions) {
      const subscriptionsContainer = this.createSubscriptionsContainer(
        providerResource,
        appPrefix,
        terraformStackResource,
        config,
        cosmosdbDatabaseResource,
        cosmosdbSqlDatabaseResource
      )

      const connectionsContainer = this.createConnectionsContainer(
        providerResource,
        appPrefix,
        terraformStackResource,
        config,
        cosmosdbDatabaseResource,
        cosmosdbSqlDatabaseResource
      )
      return [cosmosdbSqlEventContainer, subscriptionsContainer, connectionsContainer].concat(readModels)
    }
    return [cosmosdbSqlEventContainer].concat(readModels)
  }

  private static createEventContainer(
    providerResource: AzurermProvider,
    appPrefix: string,
    terraformStackResource: TerraformStack,
    config: BoosterConfig,
    cosmosdbDatabaseResource: cosmosdbAccount.CosmosdbAccount,
    cosmosdbSqlDatabaseResource: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  ): cosmosdbSqlContainer.CosmosdbSqlContainer {
    const idEvent = toTerraformName(appPrefix, 'events')
    return new cosmosdbSqlContainer.CosmosdbSqlContainer(terraformStackResource, idEvent, {
      name: config.resourceNames.eventsStore,
      resourceGroupName: cosmosdbDatabaseResource.resourceGroupName,
      accountName: cosmosdbDatabaseResource.name,
      databaseName: cosmosdbSqlDatabaseResource.name,
      partitionKeyPath: `/${eventsStoreAttributes.partitionKey}`,
      partitionKeyVersion: 2,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: providerResource,
    })
  }

  private static createReadModel(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    config: BoosterConfig,
    readModel: string,
    cosmosdbDatabaseResource: cosmosdbAccount.CosmosdbAccount,
    cosmosdbSqlDatabaseResource: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  ): cosmosdbSqlContainer.CosmosdbSqlContainer {
    const readModelName = config.resourceNames.forReadModel(readModel)
    const idReadModel = toTerraformName(readModel)
    return new cosmosdbSqlContainer.CosmosdbSqlContainer(terraformStackResource, idReadModel, {
      name: readModelName,
      resourceGroupName: cosmosdbDatabaseResource.resourceGroupName,
      accountName: cosmosdbDatabaseResource.name,
      databaseName: cosmosdbSqlDatabaseResource.name,
      partitionKeyPath: '/id',
      partitionKeyVersion: 2,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: providerResource,
    })
  }

  private static createSubscriptionsContainer(
    providerResource: AzurermProvider,
    appPrefix: string,
    terraformStackResource: TerraformStack,
    config: BoosterConfig,
    cosmosdbDatabaseResource: cosmosdbAccount.CosmosdbAccount,
    cosmosdbSqlDatabaseResource: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  ): cosmosdbSqlContainer.CosmosdbSqlContainer {
    const id = toTerraformName(appPrefix, 'subscriptions-table')
    return new cosmosdbSqlContainer.CosmosdbSqlContainer(terraformStackResource, id, {
      name: config.resourceNames.subscriptionsStore,
      resourceGroupName: cosmosdbDatabaseResource.resourceGroupName,
      accountName: cosmosdbDatabaseResource.name,
      databaseName: cosmosdbSqlDatabaseResource.name,
      partitionKeyPath: `/${subscriptionsStoreAttributes.partitionKey}`,
      partitionKeyVersion: 2,
      defaultTtl: -1,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: providerResource,
    })
  }

  private static createConnectionsContainer(
    providerResource: AzurermProvider,
    appPrefix: string,
    terraformStackResource: TerraformStack,
    config: BoosterConfig,
    cosmosdbDatabaseResource: cosmosdbAccount.CosmosdbAccount,
    cosmosdbSqlDatabaseResource: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  ): cosmosdbSqlContainer.CosmosdbSqlContainer {
    const id = toTerraformName(appPrefix, 'connections-table')
    return new cosmosdbSqlContainer.CosmosdbSqlContainer(terraformStackResource, id, {
      name: config.resourceNames.connectionsStore,
      resourceGroupName: cosmosdbDatabaseResource.resourceGroupName,
      accountName: cosmosdbDatabaseResource.name,
      databaseName: cosmosdbSqlDatabaseResource.name,
      partitionKeyPath: `/${connectionsStoreAttributes.partitionKey}`,
      partitionKeyVersion: 2,
      defaultTtl: -1,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: providerResource,
    })
  }
}
