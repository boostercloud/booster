import { TerraformStack } from 'cdktf'
import { windowsFunctionApp, resourceGroup, storageAccount, webPubsub, servicePlan } from '@cdktf/provider-azurerm'
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
    webPubsubResource: webPubsub.WebPubsub,
    appPrefix: string,
    functionAppName: string,
    cosmosDatabaseName: string,
    apiManagementServiceName: string,
    cosmosDbConnectionString: string,
    config: BoosterConfig,
    zipFile: string
  ): windowsFunctionApp.WindowsFunctionApp {
    const id = toTerraformName(appPrefix, 'func')
    return new windowsFunctionApp.WindowsFunctionApp(terraformStackResource, id, {
      name: functionAppName,
      location: resourceGroupResource.location,
      resourceGroupName: resourceGroupResource.name,
      servicePlanId: servicePlanResource.id,
      appSettings: {
        WebPubSubConnectionString: webPubsubResource.primaryConnectionString,
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
          nodeVersion: '~14',
        },
      },
      functionsExtensionVersion: '~3', // keep it on version 3. Version 4 needs a migration process
      zipDeployFile: zipFile,
    })
  }
}
