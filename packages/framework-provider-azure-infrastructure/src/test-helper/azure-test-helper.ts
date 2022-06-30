import { configuration } from '../infrastructure/helper/params'
import { getResourceGroup, getCosmosConnectionStrings } from '../infrastructure/helper/az-cli-helper'
import { ResourceGroup } from '../infrastructure/types/resource-group'
import { AzureCounters } from './azure-counters'
import { AzureQueries } from './azure-queries'
import { ProviderTestHelper } from '@boostercloud/framework-types'

interface ApplicationOutputs {
  graphqlURL: string
  websocketURL: string
}

export class AzureTestHelper implements ProviderTestHelper {
  private constructor(
    readonly outputs: ApplicationOutputs,
    readonly counters: AzureCounters,
    readonly queries: AzureQueries
  ) {}

  public static async checkResourceGroup(applicationName: string, environmentName: string): Promise<ResourceGroup> {
    return getResourceGroup(applicationName, environmentName)
  }

  public static async build(appName: string, environmentName: string): Promise<AzureTestHelper> {
    const resourceGroup = await this.checkResourceGroup(appName, environmentName)
    this.ensureAzureConfiguration()
    const cosmosConnectionString = await this.getCosmosConnection(appName, environmentName)

    return new AzureTestHelper(
      {
        graphqlURL: await this.graphqlURL(resourceGroup),
        websocketURL: await this.websocketURL(),
      },
      new AzureCounters(appName, cosmosConnectionString),
      new AzureQueries(appName, cosmosConnectionString)
    )
  }

  public static ensureAzureConfiguration(): void {
    console.log('Checking Azure configuration...')
    if (!configuration.appId || !configuration.tenantId || !configuration.secret || !configuration.subscriptionId) {
      throw new Error('Azure credentials were not properly loaded and are required to run the integration tests.')
    }
    if (!configuration.region) {
      throw new Error('Azure region was not properly loaded and is required to run the integration tests. ')
    } else {
      console.log('Azure Region set to ' + configuration.region)
    }
  }

  private static async getCosmosConnection(appName: string, environmentName: string): Promise<string> {
    const connectionDetails = await getCosmosConnectionStrings(appName, environmentName)
    const mainDbConnection = connectionDetails.connectionStrings[0]
    if (!mainDbConnection) {
      throw new Error('Unable to get cosmos connection details from current resource group')
    }
    return mainDbConnection.connectionString
  }
  public static async graphqlURL(resourceGroup: ResourceGroup): Promise<string> {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    const url = `https://${resourceGroup.name}apis.azure-api.net/${environment}/graphql`
    console.log(`service Url: ${url}`)
    return url
  }

  // TODO: Currently websocket are not supported on Azure
  private static async websocketURL(): Promise<string> {
    return ''
  }
}
