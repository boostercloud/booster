import { BoosterConfig } from '@boostercloud/framework-types'
import {
  buildAppPrefix,
  createApiManagementName,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createStreamFunctionResourceGroupName,
  readProjectConfig,
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
import { ApplicationSynthStack, StackNames } from '../types/application-synth-stack'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'
import { TerraformOutputs } from './terraform-outputs'
import { TerraformWebPubsubHub } from './terraform-web-pubsub-hub'
import { TerraformWebPubSubExtensionKey } from './terraform-web-pub-sub-extension-key'
import { TerraformEventHubNamespace } from './terraform-event-hub-namespace'
import { TerraformEventHub } from './terraform-event-hub'
import { windowsFunctionApp } from '@cdktf/provider-azurerm'
import { TerraformApiManagementApiOperationSensorHealth } from './terraform-api-management-api-operation-sensor-health'

export class ApplicationSynth {
  readonly config: BoosterConfig
  readonly stackNames: StackNames

  public constructor(terraformStack: TerraformStack) {
    this.config = readProjectConfig(process.cwd())
    const azurermProvider = new AzurermProvider(terraformStack, 'azureFeature', {
      features: {},
    })
    const appPrefix = buildAppPrefix(this.config)
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    const streamFunctionAppName = createStreamFunctionResourceGroupName(resourceGroupName)
    const apiManagementName = createApiManagementName(resourceGroupName)
    this.stackNames = {
      appPrefix: appPrefix,
      terraformStack: terraformStack,
      azureProvider: azurermProvider,
      resourceGroupName: resourceGroupName,
      functionAppName: functionAppName,
      streamFunctionAppName: streamFunctionAppName,
      apiManagementName: apiManagementName,
      eventHubName: this.config.resourceNames.streamTopic,
      webPubSubHubName: 'booster',
    }
  }

  public synth(zipFile: string): ApplicationSynthStack {
    const graphQLApiOperation = 'graphql'
    const sensorApiOperation = 'sensor-health'
    const resourceGroup = TerraformResourceGroup.build(this.stackNames)
    const stack: ApplicationSynthStack = { ...this.stackNames, resourceGroup: resourceGroup }
    stack.cosmosdbDatabase = TerraformCosmosdbDatabase.build(stack)
    stack.cosmosdbSqlDatabase = TerraformCosmosdbSqlDatabase.build(stack, this.config)
    stack.containers = TerraformContainers.build(stack, this.config)
    this.buildEventHub(zipFile, stack)
    this.buildWebPubSub(stack)
    stack.apiManagement = TerraformApiManagement.build(stack)
    stack.apiManagementApi = TerraformApiManagementApi.build(stack, this.config.environmentName)
    stack.graphQLApiManagementApiOperation = TerraformApiManagementApiOperation.build(stack, graphQLApiOperation)
    stack.applicationServicePlan = TerraformServicePlan.build(stack, 'psp', 'Y1', 1)
    stack.storageAccount = TerraformStorageAccount.build(stack, 'sp')
    stack.functionApp = this.buildDefaultFunctionApp(stack, zipFile)
    stack.graphQLApiManagementApiOperationPolicy = TerraformApiManagementApiOperationPolicy.build(
      stack,
      graphQLApiOperation
    )
    stack.sensorHealthApiManagementApiOperation = TerraformApiManagementApiOperationSensorHealth.build(
      stack,
      sensorApiOperation
    )
    stack.sensorHealthApiManagementApiOperationPolicy = TerraformApiManagementApiOperationPolicy.build(
      stack,
      sensorApiOperation
    )
    this.buildWebPubSubHub(stack)
    TerraformOutputs.build(stack)

    return stack
  }

  private buildDefaultFunctionApp(
    stack: ApplicationSynthStack,
    zipFile: string
  ): windowsFunctionApp.WindowsFunctionApp {
    return TerraformFunctionApp.build(
      stack,
      this.config,
      zipFile,
      stack.applicationServicePlan!,
      stack.storageAccount!,
      'func',
      stack.functionAppName
    )
  }

  private buildEventHub(zipFile: string, stack: ApplicationSynthStack): void {
    if (this.config.eventStreamConfiguration.enabled) {
      stack.eventHubNamespace = TerraformEventHubNamespace.build(stack)
      stack.eventHub = TerraformEventHub.build(stack, this.config)
      const instanceCount = this.config.eventStreamConfiguration.parameters?.partitionCount ?? '3'
      stack.eventConsumerServicePlan = TerraformServicePlan.build(stack, 'psc', 'B1', instanceCount)
      stack.eventConsumerStorageAccount = TerraformStorageAccount.build(stack, 'sc')
      stack.eventConsumerFunctionApp = TerraformFunctionApp.build(
        stack,
        this.config,
        zipFile,
        stack.eventConsumerServicePlan,
        stack.eventConsumerStorageAccount,
        'fhub',
        stack.streamFunctionAppName
      )
      if (!stack.containers) {
        stack.containers = []
      }
      stack.containers.push(TerraformContainers.createDedupEventsContainer(stack, this.config))
    }
  }

  private buildWebPubSub(stack: ApplicationSynthStack): void {
    if (this.config.enableSubscriptions) {
      stack.webPubSub = TerraformWebPubsub.build(stack)
    }
  }

  private buildWebPubSubHub(stack: ApplicationSynthStack) {
    if (stack.webPubSub) {
      stack.dataFunctionAppHostKeys = TerraformWebPubSubExtensionKey.build(stack)
      stack.webPubSubHub = TerraformWebPubsubHub.build(stack)
    }
  }
}
