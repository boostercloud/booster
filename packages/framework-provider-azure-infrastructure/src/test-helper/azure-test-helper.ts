import { configuration } from '../infrastructure/params'
import { getResourceGroup } from '../infrastructure/helper/az-cli-helper'
import { ResourceGroup } from '../infrastructure/types/resource-group'

export class AzureTestHelper {
  public static async checkResourceGroup(applicationName: string): Promise<ResourceGroup> {
    console.log('Check resource group')
    return getResourceGroup(applicationName)
  }

  public static async build(): Promise<AzureTestHelper> {
    this.ensureAzureConfiguration()
    return new AzureTestHelper()
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
}
