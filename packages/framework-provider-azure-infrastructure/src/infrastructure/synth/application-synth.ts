import { BoosterConfig } from '@boostercloud/framework-types'
import {
  buildAppPrefix,
  readProjectConfig,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createApiManagementName,
} from '../helper/utils'
import { TerraformStack } from 'cdktf'
import { TerraformServicePlan } from './terraform-service-plan'
import { TerraformResourceGroup } from './terraform-resource-group'
import { TerraformStorageAccount } from './terraform-storage-account'
import { TerraformFunctionApp } from './terraform-function-app'
import { TerraformCosmosdbSqlDatabase } from './terraform-cosmosdb-sql-database'
import { TerraformContainers } from './terraform-containers'
import { TerraformCosmosdbDatabase } from './terraform-cosmosdb-database'
import { TerraformApiManagement } from './terraform-api-management'
import { TerraformApiManagementApi } from './terraform-api-management-api'
import { TerraformApiManagementApiOperation } from './terraform-api-management-api-operation'
import { TerraformApiManagementApiOperationPolicy } from './terraform-api-management-api-operation-policy'
import { TerraformWebPubsub } from './terraform-web-pubsub'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'
import { TerraformOutputs } from './terraform-outputs'
import { TerraformWebPubsubHub } from './terraform-web-pubsub-hub'
import { TerraformWebPubSubExtensionKey } from './terraform-web-pub-sub-extension-key'

export class ApplicationSynth {
  readonly config: BoosterConfig
  readonly appPrefix: string
  readonly terraformStackResource: TerraformStack

  public constructor(terraformStackResource: TerraformStack) {
    this.config = readProjectConfig(process.cwd())
    this.appPrefix = buildAppPrefix(this.config)
    this.terraformStackResource = terraformStackResource
  }

  public synth(): ApplicationSynthStack {
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    const apiManagementName = createApiManagementName(resourceGroupName)
    const azurermProvider = new AzurermProvider(this.terraformStackResource, 'azureFeature', {
      features: {},
    })
    const hubName = 'booster'

    const resourceGroupResource = TerraformResourceGroup.build(
      azurermProvider,
      this.terraformStackResource,
      this.appPrefix,
      resourceGroupName
    )
    const servicePlanResource = TerraformServicePlan.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      this.appPrefix,
      resourceGroupName
    )
    const storageAccountResource = TerraformStorageAccount.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      this.appPrefix,
      resourceGroupName
    )
    const cosmosdbDatabaseResource = TerraformCosmosdbDatabase.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      this.appPrefix,
      resourceGroupName
    )
    const cosmosdbSqlDatabaseResource = TerraformCosmosdbSqlDatabase.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      this.appPrefix,
      cosmosdbDatabaseResource,
      this.config
    )
    const containersResource = TerraformContainers.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      this.appPrefix,
      cosmosdbDatabaseResource,
      cosmosdbSqlDatabaseResource,
      this.config
    )

    const webPubSubResource = TerraformWebPubsub.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      this.appPrefix
    )

    const functionAppResource = TerraformFunctionApp.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      servicePlanResource,
      storageAccountResource,
      webPubSubResource,
      this.appPrefix,
      functionAppName,
      cosmosdbDatabaseResource.name,
      apiManagementName,
      cosmosdbDatabaseResource.primaryKey,
      this.config
    )

    const apiManagementResource = TerraformApiManagement.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      apiManagementName,
      this.appPrefix
    )

    const apiManagementApiResource = TerraformApiManagementApi.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      apiManagementResource,
      this.appPrefix,
      this.config.environmentName,
      resourceGroupName
    )

    const graphQLApiManagementApiOperationResource = TerraformApiManagementApiOperation.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      apiManagementApiResource,
      this.appPrefix,
      'graphql'
    )

    const graphQLApiManagementApiOperationPolicyResource = TerraformApiManagementApiOperationPolicy.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      graphQLApiManagementApiOperationResource,
      this.appPrefix,
      this.config.environmentName,
      functionAppResource,
      'graphql'
    )

    const functionAppDataResource = TerraformWebPubSubExtensionKey.build(
      this.config,
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      functionAppResource,
      this.appPrefix
    )

    const webPubSubHubResource = TerraformWebPubsubHub.build(
      azurermProvider,
      this.terraformStackResource,
      resourceGroupResource,
      webPubSubResource,
      this.appPrefix,
      functionAppResource,
      functionAppDataResource,
      hubName
    )

    TerraformOutputs.build(
      azurermProvider,
      this.terraformStackResource,
      this.appPrefix,
      resourceGroupResource,
      graphQLApiManagementApiOperationResource,
      webPubSubResource,
      hubName
    )

    return {
      appPrefix: this.appPrefix,
      terraformStack: this.terraformStackResource,
      resourceGroupName: resourceGroupName,
      apiManagementName: apiManagementName,
      functionAppName: functionAppName,
      resourceGroup: resourceGroupResource,
      applicationServicePlan: servicePlanResource,
      storageAccount: storageAccountResource,
      functionApp: functionAppResource,
      apiManagement: apiManagementResource,
      apiManagementApi: apiManagementApiResource,
      graphQLApiManagementApiOperation: graphQLApiManagementApiOperationResource,
      graphQLApiManagementApiOperationPolicy: graphQLApiManagementApiOperationPolicyResource,
      cosmosdbDatabase: cosmosdbDatabaseResource,
      cosmosdbSqlDatabase: cosmosdbSqlDatabaseResource,
      containers: containersResource,
      webPubSub: webPubSubResource,
      webPubSubHub: webPubSubHubResource,
    } as ApplicationSynthStack
  }
}
