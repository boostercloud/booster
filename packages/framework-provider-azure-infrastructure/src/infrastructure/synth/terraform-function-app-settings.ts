import { environmentVarNames } from '@boostercloud/framework-provider-azure'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { toTerraformName } from '../helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { storageAccount } from '@cdktf/provider-azurerm'

export class TerraformFunctionAppSettings {
  static build(
    { appPrefix, cosmosdbDatabase, domainNameLabel, eventHubNamespace, eventHub, webPubSub }: ApplicationSynthStack,
    config: BoosterConfig,
    storageAccount: storageAccount.StorageAccount,
    suffixName: string
  ): { [key: string]: string } {
    if (!cosmosdbDatabase) {
      throw new Error('Undefined cosmosdbDatabase resource')
    }
    const id = toTerraformName(appPrefix, suffixName)
    const eventHubConnectionString =
      eventHubNamespace?.defaultPrimaryConnectionString && eventHub?.name
        ? `${eventHubNamespace.defaultPrimaryConnectionString};EntityPath=${eventHub.name}`
        : ''
    const region = (process.env['REGION'] ?? '').toLowerCase().replace(/ /g, '')
    return {
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
    }
  }
}
