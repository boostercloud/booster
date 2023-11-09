import {
  apiManagement,
  apiManagementApi,
  apiManagementApiOperation,
  apiManagementApiOperationPolicy,
  cosmosdbAccount,
  cosmosdbSqlContainer,
  cosmosdbSqlDatabase,
  resourceGroup,
  servicePlan,
  storageAccount,
  webPubsub,
  webPubsubHub,
  windowsFunctionApp,
} from '@cdktf/provider-azurerm'
import { TerraformResource, TerraformStack } from 'cdktf'
import { FunctionDefinition } from './functionDefinition'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export interface ApplicationSynthStack {
  appPrefix: string
  terraformStack: TerraformStack
  resourceGroupName: string | undefined
  functionAppName: string | undefined
  apiManagementName: string | undefined
  resourceGroup: resourceGroup.ResourceGroup | undefined
  applicationServicePlan: servicePlan.ServicePlan | undefined
  storageAccount: storageAccount.StorageAccount | undefined
  functionApp: windowsFunctionApp.WindowsFunctionApp | undefined
  apiManagement: apiManagement.ApiManagement | undefined
  apiManagementApi: apiManagementApi.ApiManagementApi | undefined
  graphQLApiManagementApiOperation: apiManagementApiOperation.ApiManagementApiOperation | undefined
  graphQLApiManagementApiOperationPolicy: apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy | undefined
  sensorHealthApiManagementApiOperation: apiManagementApiOperation.ApiManagementApiOperation | undefined
  sensorHealthApiManagementApiOperationPolicy:
    | apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy
    | undefined
  cosmosdbDatabase: cosmosdbAccount.CosmosdbAccount | undefined
  cosmosdbSqlDatabase: cosmosdbSqlDatabase.CosmosdbSqlDatabase | undefined
  containers: Array<cosmosdbSqlContainer.CosmosdbSqlContainer> | undefined
  webPubSub: webPubsub.WebPubsub | undefined
  webPubSubHub: webPubsubHub.WebPubsubHub | undefined
  azureProvider: AzurermProvider | undefined
  functionDefinitions?: Array<FunctionDefinition>
  rocketStack?: Array<TerraformResource>
}
