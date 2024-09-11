import { TerraformStack } from 'cdktf'
import { cosmosdbAccount, cosmosdbSqlContainer, cosmosdbSqlDatabase } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import {
  connectionsStoreAttributes,
  dedupAttributes,
  eventsStoreAttributes,
  subscriptionsStoreAttributes,
} from '@boostercloud/framework-provider-azure'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

import { MAX_CONTAINER_THROUGHPUT } from '../constants'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformContainers {
  static build(
    { terraformStack, azureProvider, appPrefix, cosmosdbDatabase, cosmosdbSqlDatabase }: ApplicationSynthStack,
    config: BoosterConfig
  ): Array<cosmosdbSqlContainer.CosmosdbSqlContainer> {
    if (!cosmosdbDatabase) {
      throw new Error('Undefined cosmosdbDatabase resource')
    }
    if (!cosmosdbSqlDatabase) {
      throw new Error('Undefined cosmosdbSqlDatabase resource')
    }
    const cosmosdbSqlEventContainer = this.createEventContainer(
      azureProvider,
      appPrefix,
      terraformStack,
      config,
      cosmosdbDatabase,
      cosmosdbSqlDatabase
    )
    const dispatchedEventsContainer = this.createDispatchedEventsContainer(
      azureProvider,
      appPrefix,
      terraformStack,
      config,
      cosmosdbDatabase,
      cosmosdbSqlDatabase
    )
    const readModels = Object.keys(config.readModels).map((readModel) =>
      this.createReadModel(azureProvider, terraformStack, config, readModel, cosmosdbDatabase, cosmosdbSqlDatabase)
    )
    if (config.enableSubscriptions) {
      const subscriptionsContainer = this.createSubscriptionsContainer(
        azureProvider,
        appPrefix,
        terraformStack,
        config,
        cosmosdbDatabase,
        cosmosdbSqlDatabase
      )

      const connectionsContainer = this.createConnectionsContainer(
        azureProvider,
        appPrefix,
        terraformStack,
        config,
        cosmosdbDatabase,
        cosmosdbSqlDatabase
      )
      return [
        cosmosdbSqlEventContainer,
        dispatchedEventsContainer,
        subscriptionsContainer,
        connectionsContainer,
      ].concat(readModels)
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
      partitionKeyPaths: [`/${eventsStoreAttributes.partitionKey}`],
      partitionKeyVersion: 2,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: providerResource,
    })
  }

  private static createDispatchedEventsContainer(
    providerResource: AzurermProvider,
    appPrefix: string,
    terraformStackResource: TerraformStack,
    config: BoosterConfig,
    cosmosdbDatabaseResource: cosmosdbAccount.CosmosdbAccount,
    cosmosdbSqlDatabaseResource: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  ): cosmosdbSqlContainer.CosmosdbSqlContainer {
    const idEvent = toTerraformName(appPrefix, 'dispatched-events')
    return new cosmosdbSqlContainer.CosmosdbSqlContainer(terraformStackResource, idEvent, {
      name: config.resourceNames.dispatchedEventsStore,
      resourceGroupName: cosmosdbDatabaseResource.resourceGroupName,
      accountName: cosmosdbDatabaseResource.name,
      databaseName: cosmosdbSqlDatabaseResource.name,
      partitionKeyPaths: ['/eventId'],
      partitionKeyVersion: 2,
      uniqueKey: [{ paths: ['/eventId'] }],
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      defaultTtl: config.dispatchedEventsTtl,
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
      partitionKeyPaths: ['/id'],
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
      partitionKeyPaths: [`/${subscriptionsStoreAttributes.partitionKey}`],
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
      partitionKeyPaths: [`/${connectionsStoreAttributes.partitionKey}`],
      partitionKeyVersion: 2,
      defaultTtl: -1,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: providerResource,
    })
  }

  static createDedupEventsContainer(
    { terraformStack, azureProvider, appPrefix, cosmosdbDatabase, cosmosdbSqlDatabase }: ApplicationSynthStack,
    config: BoosterConfig
  ): cosmosdbSqlContainer.CosmosdbSqlContainer {
    if (!cosmosdbDatabase) {
      throw new Error('Undefined cosmosdbDatabase resource')
    }
    if (!cosmosdbSqlDatabase) {
      throw new Error('Undefined cosmosdbSqlDatabase resource')
    }
    const id = toTerraformName(appPrefix, 'dedup-table')
    return new cosmosdbSqlContainer.CosmosdbSqlContainer(terraformStack, id, {
      name: config.resourceNames.eventsDedup,
      resourceGroupName: cosmosdbDatabase.resourceGroupName,
      accountName: cosmosdbDatabase.name,
      databaseName: cosmosdbSqlDatabase.name,
      partitionKeyPaths: [`/${dedupAttributes.partitionKey}`],
      uniqueKey: [{ paths: [`/${dedupAttributes.partitionKey}`] }],
      partitionKeyVersion: 2,
      defaultTtl: -1,
      autoscaleSettings: {
        maxThroughput: MAX_CONTAINER_THROUGHPUT,
      },
      provider: azureProvider,
    })
  }
}
