import { configuration } from '../infrastructure/helper/params'
import {
  getResourceGroup,
  showResourceInfo,
  showResourcesInResourceGroup,
  getCosmosConnectionStrings,
} from '../infrastructure/helper/az-cli-helper'
import { ResourceGroup } from '../infrastructure/types/resource-group'
import { AzureCounters } from './azure-counters'
import { AzureQueries } from './azure-queries'
import { Resource } from '../infrastructure/types/resource'
import { waitForIt } from '../infrastructure/helper/utils'

interface ApplicationOutputs {
  graphqlURL: string
  websocketURL: string
}

export class AzureTestHelper {
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
  private static async graphqlURL(resourceGroup: ResourceGroup): Promise<string> {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    const resourceType = 'Microsoft.ApiManagement/service'
    const apiResource = await AzureTestHelper.getResourceWithType(resourceGroup, resourceType)
    if (!apiResource?.name) {
      throw new Error('Unable to find a valid name for the resource group')
    }
    const apiGateway = await showResourceInfo(resourceGroup.name, apiResource?.name, resourceType)
    if (!apiGateway.properties?.gatewayUrl) {
      throw new Error('Unable to get the Base HTTP URL from the current resource group')
    }
    const url = `${apiGateway.properties?.gatewayUrl}/${environment}/graphql`
    console.log(`service Url: ${url}`)
    return url
  }

  private static async getResourceWithType(
    resourceGroup: ResourceGroup,
    resourceType: string
  ): Promise<Resource | undefined> {
    const resourceWithType = await waitForIt(
      async () => {
        const resources = await showResourcesInResourceGroup(resourceGroup.name)
        return resources.find((element) => element.type === resourceType)
      },
      (result) => result != null,
      'Unable to find a valid resource in the resource group'
    )
    return resourceWithType
  }

  // TODO: Currently websocket are not supported on Azure
  private static async websocketURL(): Promise<string> {
    return ''
  }
}
