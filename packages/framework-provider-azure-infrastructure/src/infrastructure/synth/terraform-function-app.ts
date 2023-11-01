import { TerraformStack } from 'cdktf'
import { resourceGroup, servicePlan, storageAccount, webPubsub, windowsFunctionApp } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformFunctionApp {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    servicePlanResource: servicePlan.ServicePlan,
    storageAccountResource: storageAccount.StorageAccount,
    appPrefix: string,
    functionAppName: string,
    cosmosDatabaseName: string,
    apiManagementServiceName: string,
    cosmosDbConnectionString: string,
    config: BoosterConfig,
    zipFile: string,
    webPubsubResource?: webPubsub.WebPubsub
  ): windowsFunctionApp.WindowsFunctionApp {
    const id = toTerraformName(appPrefix, 'func')
    return new windowsFunctionApp.WindowsFunctionApp(terraformStackResource, id, {
      name: functionAppName,
      location: resourceGroupResource.location,
      resourceGroupName: resourceGroupResource.name,
      servicePlanId: servicePlanResource.id,
      appSettings: {
        WebPubSubConnectionString: webPubsubResource?.primaryConnectionString || '',
        WEBSITE_RUN_FROM_PACKAGE: '1',
        WEBSITE_CONTENTSHARE: id,
        ...config.env,
        BOOSTER_ENV: config.environmentName,
        BOOSTER_REST_API_URL: `https://${apiManagementServiceName}.azure-api.net/${config.environmentName}`,
        COSMOSDB_CONNECTION_STRING: `AccountEndpoint=https://${cosmosDatabaseName}.documents.azure.com:443/;AccountKey=${cosmosDbConnectionString};`,
      },
      storageAccountName: storageAccountResource.name,
      storageAccountAccessKey: storageAccountResource.primaryAccessKey,
      dependsOn: [resourceGroupResource],
      lifecycle: {
        ignoreChanges: ['app_settings["WEBSITE_RUN_FROM_PACKAGE"]'],
      },
      provider: providerResource,
      siteConfig: {
        applicationStack: {
          nodeVersion: '~18',
        },
      },
      functionsExtensionVersion: '~4',
      zipDeployFile: zipFile,
    })
  }
}
