import {
  ApiManagement,
  ApiManagementApi,
  ApiManagementApiOperation,
  ApiManagementApiOperationPolicy,
  AppServicePlan,
  CosmosdbAccount,
  CosmosdbSqlContainer,
  CosmosdbSqlDatabase,
  FunctionApp,
  ResourceGroup,
  StorageAccount,
} from '@cdktf/provider-azurerm'
import { TerraformResource, TerraformStack } from 'cdktf'
import { FunctionDefinition } from './functionDefinition'

export interface ApplicationSynthStack {
  appPrefix: string
  terraformStack: TerraformStack
  resourceGroupName: string | undefined
  functionAppName: string | undefined
  resourceGroup: ResourceGroup | undefined
  applicationServicePlan: AppServicePlan | undefined
  storageAccount: StorageAccount | undefined
  functionApp: FunctionApp | undefined
  apiManagement: ApiManagement | undefined
  apiManagementApi: ApiManagementApi | undefined
  apiManagementApiOperation: ApiManagementApiOperation | undefined
  apiManagementApiOperationPolicy: ApiManagementApiOperationPolicy | undefined
  cosmosdbDatabase: CosmosdbAccount | undefined
  cosmosdbSqlDatabase: CosmosdbSqlDatabase | undefined
  containers: Array<CosmosdbSqlContainer> | undefined
  functionDefinitions?: Array<FunctionDefinition>
  rocketStack?: Array<TerraformResource>
}
