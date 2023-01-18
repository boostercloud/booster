import { TerraformStack } from 'cdktf'
import {
  dataAzurermFunctionAppHostKeys,
  functionAppFunction,
  resourceGroup,
  windowsFunctionApp,
} from '@cdktf/provider-azurerm'
import { toTerraformName } from '../../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'
import { sleep } from '@cdktf/provider-time'

export class TerraformFunctionAppData {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    functionApp: windowsFunctionApp.WindowsFunctionApp,
    appPrefix: string,
    sleepResource: sleep.Sleep,
    dummyFunction: functionAppFunction.FunctionAppFunction
  ): dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys {
    const id = toTerraformName(appPrefix, 'dataf')

    return new dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys(terraformStackResource, id, {
      name: functionApp.name,
      resourceGroupName: resourceGroupResource.name,
      provider: providerResource,
      dependsOn: [sleepResource, dummyFunction],
    })
  }
}
