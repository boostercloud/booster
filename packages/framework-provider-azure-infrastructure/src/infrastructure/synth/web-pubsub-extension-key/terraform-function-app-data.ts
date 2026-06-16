import { TerraformStack } from 'cdktn'
import { dataAzurermFunctionAppHostKeys, resourceGroup, windowsFunctionApp } from '@cdktn/provider-azurerm'
import { toTerraformName } from '../../helper/utils'
import { AzurermProvider } from '@cdktn/provider-azurerm/lib/provider'
import { sleep } from '@cdktn/provider-time'

export class TerraformFunctionAppData {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    functionApp: windowsFunctionApp.WindowsFunctionApp,
    appPrefix: string,
    sleepResource: sleep.Sleep
  ): dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys {
    const id = toTerraformName(appPrefix, 'dataf')

    return new dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys(terraformStackResource, id, {
      name: functionApp.name,
      resourceGroupName: resourceGroupResource.name,
      provider: providerResource,
      dependsOn: [sleepResource, functionApp],
    })
  }
}
