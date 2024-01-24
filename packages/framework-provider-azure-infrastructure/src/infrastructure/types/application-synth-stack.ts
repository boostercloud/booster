import {
  apiManagement,
  apiManagementApi,
  apiManagementApiOperation,
  apiManagementApiOperationPolicy,
  cosmosdbAccount,
  cosmosdbSqlContainer,
  cosmosdbSqlDatabase,
  dataAzurermFunctionAppHostKeys,
  eventhub,
  eventhubNamespace,
  resourceGroup,
  servicePlan,
  storageAccount,
  webPubsub,
  webPubsubHub,
  windowsFunctionApp
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
  apiManagementName: string
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
  apiManagement?: apiManagement.ApiManagement
  apiManagementApi?: apiManagementApi.ApiManagementApi
  graphQLApiManagementApiOperation?: apiManagementApiOperation.ApiManagementApiOperation
  graphQLApiManagementApiOperationPolicy?: apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy
  sensorHealthApiManagementApiOperation?: apiManagementApiOperation.ApiManagementApiOperation
  sensorHealthApiManagementApiOperationPolicy?: apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy
  cosmosdbDatabase?: cosmosdbAccount.CosmosdbAccount
  cosmosdbSqlDatabase?: cosmosdbSqlDatabase.CosmosdbSqlDatabase
  containers?: Array<cosmosdbSqlContainer.CosmosdbSqlContainer>
  webPubSub?: webPubsub.WebPubsub
  webPubSubHub?: webPubsubHub.WebPubsubHub
  functionDefinitions?: Array<FunctionDefinition>
  consumerFunctionDefinitions?: Array<FunctionDefinition>
  eventHubNamespace?: eventhubNamespace.EventhubNamespace
  eventHub?: eventhub.Eventhub
  rocketStack?: Array<TerraformResource>
}
