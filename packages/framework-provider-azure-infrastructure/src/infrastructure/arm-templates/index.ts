import * as CosmosDbAccount from './cosmos-db-account'
import * as StorageAccount from './storage-account'
import * as FunctionApp from './function-app'
import * as ApiManagement from './api-management'

export const armTemplates = {
  cosmosDbAccount: CosmosDbAccount.template,
  storageAccount: StorageAccount.template,
  functionApp: FunctionApp.template,
  apiManagement: ApiManagement.template,
}
