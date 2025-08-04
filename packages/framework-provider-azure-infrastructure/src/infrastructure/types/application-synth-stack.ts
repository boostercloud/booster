import {
  apiManagementApi,
  appConfiguration,
  applicationGateway,
  cosmosdbAccount,
  cosmosdbSqlContainer,
  cosmosdbSqlDatabase,
  dataAzurermFunctionAppHostKeys,
  dataAzurermPublicIp,
  eventhub,
  eventhubNamespace,
  networkSecurityGroup,
  publicIp,
  resourceGroup,
  servicePlan,
  storageAccount,
  subnet,
  subnetNetworkSecurityGroupAssociation,
  virtualNetwork,
  webPubsub,
  webPubsubHub,
  windowsFunctionApp,
} from '@cdktf/provider-azurerm'
import { TerraformResource, TerraformStack } from 'cdktf'
import { FunctionDefinition } from './functionDefinition'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export interface StackNames {
  appPrefix: string
  terraformStack: TerraformStack
  azureProvider: AzurermProvider
  resourceGroupName: string
  functionAppName: string
  streamFunctionAppName: string
  domainNameLabel: string
  eventHubName: string
  webPubSubHubName: string
}

export interface ApplicationSynthStack extends StackNames {
  resourceGroup: resourceGroup.ResourceGroup
  applicationServicePlan?: servicePlan.ServicePlan
  storageAccount?: storageAccount.StorageAccount
  functionApp?: windowsFunctionApp.WindowsFunctionApp
  eventConsumerServicePlan?: servicePlan.ServicePlan
  eventConsumerStorageAccount?: storageAccount.StorageAccount
  eventConsumerFunctionApp?: windowsFunctionApp.WindowsFunctionApp
  dataFunctionAppHostKeys?: dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys
  appGateway?: applicationGateway.ApplicationGateway
  networkSecurityGroup?: networkSecurityGroup.NetworkSecurityGroup
  subnet?: subnet.Subnet
  subnetNetworkSecurityGroupAssociation?: subnetNetworkSecurityGroupAssociation.SubnetNetworkSecurityGroupAssociation
  virtualNetwork?: virtualNetwork.VirtualNetwork
  publicIP?: publicIp.PublicIp
  publicIPData?: dataAzurermPublicIp.DataAzurermPublicIp
  apiManagementApi?: apiManagementApi.ApiManagementApi
  cosmosdbDatabase?: cosmosdbAccount.CosmosdbAccount
  cosmosdbSqlDatabase?: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  containers?: Array<cosmosdbSqlContainer.CosmosdbSqlContainer>
  webPubSub?: webPubsub.WebPubsub
  webPubSubHub?: webPubsubHub.WebPubsubHub
  functionDefinitions?: Array<FunctionDefinition>
  consumerFunctionDefinitions?: Array<FunctionDefinition>
  eventHubNamespace?: eventhubNamespace.EventhubNamespace
  eventHub?: eventhub.Eventhub
  appConfiguration?: appConfiguration.AppConfiguration
  rocketStack?: Array<TerraformResource>
}
