import { configuration } from '../infrastructure/params'
import { getResourceGroup } from '../infrastructure/helper/az-cli-helper'
import { ResourceGroup } from '../infrastructure/types/resource-group'
import { AzureCounters } from './azure-counters'
import { AzureQueries } from './azure-queries'

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

  public static async checkResourceGroup(applicationName: string): Promise<ResourceGroup> {
    console.log('Check resource group')
    return getResourceGroup(applicationName)
  }

  public static async build(): Promise<AzureTestHelper> {
    this.ensureAzureConfiguration()
    return new AzureTestHelper(
      {
        graphqlURL: await this.graphqlURL(),
        websocketURL: await this.websocketURL(),
      },
      new AzureCounters(db, config),
      new AzureQueries()
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

  // todo
  private static async graphqlURL(): Promise<string> {
    const url = stack.Outputs?.find((output) => {
      return output.OutputKey === 'graphqlURL'
    })?.OutputValue

    if (!url) {
      throw 'Unable to get the Base HTTP URL from the current stack'
    }
    return url
  }

  // todo
  private static async websocketURL(): Promise<string> {
    const url = stack.Outputs?.find((output) => {
      return output.OutputKey === 'websocketURL'
    })?.OutputValue

    if (!url) {
      throw 'Unable to get the Base Websocket URL from the current stack'
    }
    return url
  }
}
