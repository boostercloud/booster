import { TerraformStack } from 'cdktf'
import { functionAppFunction, resourceGroup, windowsFunctionApp } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'
import { WebsocketMessagesFunction } from '../../functions/websocket-messages-function'
import { BoosterConfig } from '@boostercloud/framework-types'

export class TerraformFunctionAppDummyFunction {
  static build(
    config: BoosterConfig,
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    functionApp: windowsFunctionApp.WindowsFunctionApp,
    appPrefix: string
  ): functionAppFunction.FunctionAppFunction {
    const id = toTerraformName(appPrefix, 'fafunc')

    const functionDefinition = new WebsocketMessagesFunction(config).getFunctionDefinition()
    const configJSON = JSON.stringify({
      bindings: [...functionDefinition.config.bindings],
    })
    return new functionAppFunction.FunctionAppFunction(terraformStackResource, id, {
      name: 'baseWebPubSubBinding',
      functionAppId: functionApp.id,
      configJson: configJSON,
      provider: providerResource,
    })
  }
}
