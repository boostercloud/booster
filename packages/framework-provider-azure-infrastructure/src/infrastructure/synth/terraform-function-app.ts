import { TerraformStack } from 'cdktf'
import { AppServicePlan, FunctionApp, ResourceGroup, StorageAccount } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'

export class TerraformFunctionApp {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    applicationServicePlan: AppServicePlan,
    storageAccount: StorageAccount,
    appPrefix: string,
    functionAppName: string,
    cosmosDatabaseName: string,
    apiManagementServiceName: string,
    cosmosDbConnectionString: string,
    config: BoosterConfig
  ): FunctionApp {
    const id = toTerraformName(appPrefix, 'func')
    return new FunctionApp(terraformStack, id, {
      name: functionAppName,
      location: resourceGroup.location,
      resourceGroupName: resourceGroup.name,
      appServicePlanId: applicationServicePlan.id,
      appSettings: {
        FUNCTIONS_WORKER_RUNTIME: 'node',
        AzureWebJobsStorage: storageAccount.primaryConnectionString,
        WEBSITE_CONTENTAZUREFILECONNECTIONSTRING: storageAccount.primaryConnectionString,
        WEBSITE_RUN_FROM_PACKAGE: '',
        WEBSITE_CONTENTSHARE: id,
        WEBSITE_NODE_DEFAULT_VERSION: '~14',
        ...config.env,
        BOOSTER_ENV: config.environmentName,
        BOOSTER_REST_API_URL: `https://${apiManagementServiceName}.azure-api.net/${config.environmentName}`,
        COSMOSDB_CONNECTION_STRING: `AccountEndpoint=https://${cosmosDatabaseName}.documents.azure.com:443/;AccountKey=${cosmosDbConnectionString};`,
      },
      osType: 'linux',
      storageAccountName: storageAccount.name,
      storageAccountAccessKey: storageAccount.primaryAccessKey,
      version: '~3',
      dependsOn: [resourceGroup],
      lifecycle: {
        ignoreChanges: ['app_settings["WEBSITE_RUN_FROM_PACKAGE"]'],
      },
    })
  }
}
