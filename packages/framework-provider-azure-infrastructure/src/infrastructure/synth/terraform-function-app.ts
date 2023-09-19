import { servicePlan, storageAccount, windowsFunctionApp } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { environmentVarNames } from '@boostercloud/framework-provider-azure'

export class TerraformFunctionApp {
  static build(
    {
      terraformStack,
      azureProvider,
      appPrefix,
      resourceGroup,
      resourceGroupName,
      cosmosdbDatabase,
      apiManagementName,
      eventHubNamespace,
      eventHub,
      webPubSub,
    }: ApplicationSynthStack,
    config: BoosterConfig,
    zipFile: string,
    applicationServicePlan: servicePlan.ServicePlan,
    storageAccount: storageAccount.StorageAccount,
    suffixName: string,
    functionAppName: string
  ): windowsFunctionApp.WindowsFunctionApp {
    if (!cosmosdbDatabase) {
      throw new Error('Undefined cosmosdbDatabase resource')
    }
    if (!applicationServicePlan) {
      throw new Error('Undefined applicationServicePlan resource')
    }
    const id = toTerraformName(appPrefix, suffixName)
    const eventHubConnectionString =
      eventHubNamespace?.defaultPrimaryConnectionString && eventHub?.name
        ? `${eventHubNamespace.defaultPrimaryConnectionString};EntityPath=${eventHub.name}`
        : ''
    return new windowsFunctionApp.WindowsFunctionApp(terraformStack, id, {
      name: functionAppName,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      servicePlanId: applicationServicePlan.id,
      appSettings: {
        WEBSITE_RUN_FROM_PACKAGE: '1',
        WEBSITE_CONTENTSHARE: id,
        ...config.env,
        WebPubSubConnectionString: webPubSub?.primaryConnectionString || '',
        BOOSTER_ENV: config.environmentName,
        BOOSTER_REST_API_URL: `https://${apiManagementName}.azure-api.net/${config.environmentName}`,
        [environmentVarNames.eventHubConnectionString]: eventHubConnectionString,
        [environmentVarNames.eventHubName]: config.resourceNames.streamTopic,
        COSMOSDB_CONNECTION_STRING: `AccountEndpoint=https://${cosmosdbDatabase.name}.documents.azure.com:443/;AccountKey=${cosmosdbDatabase.primaryKey};`,
        WEBSITE_CONTENTAZUREFILECONNECTIONSTRING: storageAccount.primaryConnectionString, // Terraform bug: https://github.com/hashicorp/terraform-provider-azurerm/issues/16650
      },
      storageAccountName: storageAccount.name,
      storageAccountAccessKey: storageAccount.primaryAccessKey,
      dependsOn: [resourceGroup],
      lifecycle: {
        ignoreChanges: ['app_settings["WEBSITE_RUN_FROM_PACKAGE"]'],
      },
      provider: azureProvider,
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
