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
    functionAppName: string
  ): FunctionApp {
    const id = toTerraformName(appPrefix, 'func')
    const functionApp = new FunctionApp(terraformStack, id, {
      name: functionAppName,
      location: resourceGroup.location,
      resourceGroupName: resourceGroup.name,
      appServicePlanId: applicationServicePlan.id,
      appSettings: {
        FUNCTIONS_WORKER_RUNTIME: 'node',
        AzureWebJobsStorage: storageAccount.primaryConnectionString,
        WEBSITE_CONTENTAZUREFILECONNECTIONSTRING: storageAccount.primaryConnectionString,
        WEBSITE_RUN_FROM_PACKAGE: 'ThisWillBeSetToAnURLByAzureDevOpsDeploy',
        WEBSITE_CONTENTSHARE: id,
        WEBSITE_NODE_DEFAULT_VERSION: '~14',
      },
      osType: 'linux',
      storageAccountName: storageAccount.name,
      storageAccountAccessKey: storageAccount.primaryAccessKey,
      version: '~3',
      dependsOn: [resourceGroup],
      tags: { local: 'tags' },
    })
    functionApp.lifecycle = {
      ignoreChanges: ['app_settings["WEBSITE_RUN_FROM_PACKAGE"]', 'app_settings["WEBSITE_ENABLE_SYNC_UPDATE_SITE"]'],
    }
    return functionApp
  }

  static updateFunction(
    functionApp: FunctionApp,
    functionAppName: string,
    apiManagementServiceName: string,
    cosmosDbConnectionString: string,
    config: BoosterConfig
  ): FunctionApp {
    functionApp.addOverride('app_settings', {
      ...config.env,
      BOOSTER_ENV: config.environmentName,
      BOOSTER_REST_API_URL: `https://${apiManagementServiceName}.azure-api.net/${config.environmentName}`,
      COSMOSDB_CONNECTION_STRING: `AccountEndpoint=https://${functionAppName}.documents.azure.com:443/;AccountKey=${cosmosDbConnectionString};`,
    })
    return functionApp
  }
}
