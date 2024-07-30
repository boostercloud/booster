import { servicePlan, storageAccount, windowsFunctionApp } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { environmentVarNames } from '@boostercloud/framework-provider-azure'
import { WindowsFunctionAppConfig } from '@cdktf/provider-azurerm/lib/windows-function-app'

export class TerraformFunctionApp {
  static build(
    {
      terraformStack,
      azureProvider,
      appPrefix,
      resourceGroup,
      resourceGroupName,
      cosmosdbDatabase,
      domainNameLabel,
      eventHubNamespace,
      eventHub,
      webPubSub,
    }: ApplicationSynthStack,
    config: BoosterConfig,
    applicationServicePlan: servicePlan.ServicePlan,
    storageAccount: storageAccount.StorageAccount,
    suffixName: string,
    functionAppName: string,
    zipFile?: string
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
    const region = (process.env['REGION'] ?? '').toLowerCase().replace(/ /g, '')
    const functionConfig: Exclude<WindowsFunctionAppConfig, 'zipDeployFile'> = {
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
        [environmentVarNames.restAPIURL]: `http://${domainNameLabel}.${region}.cloudapp.azure.com/${config.environmentName}`,
        [environmentVarNames.eventHubConnectionString]: eventHubConnectionString,
        [environmentVarNames.eventHubName]: config.resourceNames.streamTopic,
        [environmentVarNames.eventHubMaxRetries]:
          config.eventStreamConfiguration.parameters?.maxRetries?.toString() || '5',
        [environmentVarNames.eventHubMode]: config.eventStreamConfiguration.parameters?.mode || 'exponential',
        COSMOSDB_CONNECTION_STRING: `AccountEndpoint=https://${cosmosdbDatabase.name}.documents.azure.com:443/;AccountKey=${cosmosdbDatabase.primaryKey};`,
        WEBSITE_CONTENTAZUREFILECONNECTIONSTRING: storageAccount.primaryConnectionString, // Terraform bug: https://github.com/hashicorp/terraform-provider-azurerm/issues/16650
        BOOSTER_APP_NAME: process.env['BOOSTER_APP_NAME'] ?? '',
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
    }
    if (zipFile) {
      return new windowsFunctionApp.WindowsFunctionApp(terraformStack, id, {
        ...functionConfig,
        zipDeployFile: zipFile,
      })
    }
    return new windowsFunctionApp.WindowsFunctionApp(terraformStack, id, functionConfig)
  }
}
