import { BoosterConfig } from '@boostercloud/framework-types'
import {
  buildAppPrefix,
  createDomainNameLabel,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createStreamFunctionResourceGroupName,
  readProjectConfig,
  toTerraformName,
} from '../helper/utils'
import { TerraformStack } from 'cdktf'
import { TerraformServicePlan } from './terraform-service-plan'
import { TerraformResourceGroup } from './terraform-resource-group'
import { TerraformStorageAccount } from './terraform-storage-account'
import { TerraformFunctionApp } from './terraform-function-app'
import { TerraformCosmosdbSqlDatabase } from './terraform-cosmosdb-sql-database'
import { TerraformContainers } from './terraform-containers'
import { TerraformCosmosdbDatabase } from './terraform-cosmosdb-database'
import { TerraformApplicationGateway } from './gateway/terraform-application-gateway'
import { TerraformWebPubsub } from './terraform-web-pubsub'
import { ApplicationSynthStack, StackNames } from '../types/application-synth-stack'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'
import { TerraformOutputs } from './terraform-outputs'
import { TerraformWebPubsubHub } from './terraform-web-pubsub-hub'
import { TerraformWebPubSubExtensionKey } from './terraform-web-pub-sub-extension-key'
import { TerraformEventHubNamespace } from './terraform-event-hub-namespace'
import { TerraformEventHub } from './terraform-event-hub'
import { windowsFunctionApp } from '@cdktf/provider-azurerm'
import { TerraformNetworkSecurityGroup } from './gateway/terraform-network-security-group'
import { TerraformVirtualNetwork } from './gateway/terraform-virtual-network'
import { TerraformPublicIp } from './gateway/terraform-public-ip'
import { TerraformPublicIpData } from './gateway/terraform-public-ip-data'

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
    const domainNameLabel = createDomainNameLabel(resourceGroupName)
    this.stackNames = {
      appPrefix: appPrefix,
      terraformStack: terraformStack,
      azureProvider: azurermProvider,
      resourceGroupName: resourceGroupName,
      functionAppName: functionAppName,
      streamFunctionAppName: streamFunctionAppName,
      domainNameLabel: domainNameLabel,
      eventHubName: this.config.resourceNames.streamTopic,
      webPubSubHubName: 'booster',
    }
  }

  public synth(zipFile?: string): ApplicationSynthStack {
    const resourceGroup = TerraformResourceGroup.build(this.stackNames)
    const stack: ApplicationSynthStack = { ...this.stackNames, resourceGroup: resourceGroup }

    stack.networkSecurityGroup = TerraformNetworkSecurityGroup.build(
      stack,
      toTerraformName(this.stackNames.appPrefix, 'sg'),
      `${this.stackNames.resourceGroupName}rjsg`
    )
    stack.virtualNetwork = TerraformVirtualNetwork.build(stack)
    stack.publicIP = TerraformPublicIp.build(stack, 'pip')
    stack.appGateway = TerraformApplicationGateway.build(stack)
    stack.publicIPData = TerraformPublicIpData.build(stack)

    stack.cosmosdbDatabase = TerraformCosmosdbDatabase.build(stack)
    stack.cosmosdbSqlDatabase = TerraformCosmosdbSqlDatabase.build(stack, this.config)
    stack.containers = TerraformContainers.build(stack, this.config)
    this.buildEventHub(stack)
    this.buildWebPubSub(stack)
    stack.applicationServicePlan = TerraformServicePlan.build(stack, 'psp', 'Y1', 1)
    stack.storageAccount = TerraformStorageAccount.build(stack, 'sp')
    stack.functionApp = this.buildDefaultFunctionApp(stack, zipFile)
    this.buildWebPubSubHub(stack)
    TerraformOutputs.build(stack)

    return stack
  }

  private buildDefaultFunctionApp(
    stack: ApplicationSynthStack,
    zipFile?: string
  ): windowsFunctionApp.WindowsFunctionApp {
    return TerraformFunctionApp.build(
      stack,
      this.config,
      stack.applicationServicePlan!,
      stack.storageAccount!,
      'func',
      stack.functionAppName,
      zipFile
    )
  }

  private buildEventHub(stack: ApplicationSynthStack): void {
    if (this.config.eventStreamConfiguration.enabled) {
      stack.eventHubNamespace = TerraformEventHubNamespace.build(stack)
      stack.eventHub = TerraformEventHub.build(stack, this.config)
      const instanceCount = this.config.eventStreamConfiguration.parameters?.partitionCount ?? '3'
      stack.eventConsumerServicePlan = TerraformServicePlan.build(stack, 'psc', 'B1', instanceCount)
      stack.eventConsumerStorageAccount = TerraformStorageAccount.build(stack, 'sc')
      stack.eventConsumerFunctionApp = TerraformFunctionApp.build(
        stack,
        this.config,
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
