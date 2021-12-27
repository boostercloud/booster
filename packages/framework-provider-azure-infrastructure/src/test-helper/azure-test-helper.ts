import { configuration } from '../infrastructure/helper/params'
import {
  getResourceGroup,
  showResourceInfo,
  showResourcesInResourceGroup,
} from '../infrastructure/helper/az-cli-helper'
import { ResourceGroup } from '../infrastructure/types/resource-group'
import { AzureCounters } from './azure-counters'
import { AzureQueries } from './azure-queries'
import { Resource } from '../infrastructure/types/resource'

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
    console.log('Check resource group')
    return getResourceGroup(applicationName, environmentName)
  }

  public static async build(appName: string, environmentName: string): Promise<AzureTestHelper> {
    console.log('Application name: ', appName)
    const resourceGroup = await this.checkResourceGroup(appName, environmentName)
    this.ensureAzureConfiguration()
    const cosmosConnectionString = await this.getCosmosConnection(resourceGroup)

    return new AzureTestHelper(
      {
        graphqlURL: await this.graphqlURL(resourceGroup),
        websocketURL: await this.websocketURL(),
      },
      new AzureCounters(appName),
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

  private static async getCosmosConnection(resourceGroup: ResourceGroup): Promise<string> {
    const resourceType = 'Microsoft.DocumentDB/databaseAccounts'
    const cosmosResource = await AzureTestHelper.getResourceWithType(resourceGroup, resourceType)
    console.log(cosmosResource)
    if (!cosmosResource.properties?.gatewayUrl) {
      throw new Error('Unable to get the Base HTTP URL from the current resource group')
    }
    return ''
  }
  private static async graphqlURL(resourceGroup: ResourceGroup): Promise<string> {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    const resourceType = 'Microsoft.ApiManagement/service'
    const apiResource = await AzureTestHelper.getResourceWithType(resourceGroup, resourceType)
    const apiGateway = await showResourceInfo(resourceGroup.name, apiResource.name, resourceType)
    if (!apiGateway.properties?.gatewayUrl) {
      throw new Error('Unable to get the Base HTTP URL from the current resource group')
    }
    const url = `${apiGateway.properties?.gatewayUrl}/${environment}/graphql`
    console.log(`service Url: ${url}`)
    return url
  }

  private static async getResourceWithType(resourceGroup: ResourceGroup, resourceType: string): Promise<Resource> {
    const resources = await showResourcesInResourceGroup(resourceGroup.name)
    const resourceWithType = await resources.find((element) => element.type === resourceType)
    if (!resourceWithType) {
      throw new Error('Unable to find a valid resource in the resource group')
    }
    return resourceWithType
  }

  // TODO: Currently websocket are not supported on Azure
  private static async websocketURL(): Promise<string> {
    return ''
  }
}
