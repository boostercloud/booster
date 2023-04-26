import { TerraformStack } from 'cdktf'
import { toTerraformName } from '../helper/utils'
import {
  dataAzurermFunctionAppHostKeys,
  resourceGroup,
  webPubsub,
  webPubsubHub,
  windowsFunctionApp,
} from '@cdktf/provider-azurerm'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformWebPubsubHub {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    webPubSubResource: webPubsub.WebPubsub,
    appPrefix: string,
    functionApp: windowsFunctionApp.WindowsFunctionApp,
    dataAzurermFunctionAppHostKeys: dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys,
    hubName: string
  ): webPubsubHub.WebPubsubHub {
    const idApiManagement = toTerraformName(appPrefix, 'wpsh')

    return new webPubsubHub.WebPubsubHub(terraformStackResource, idApiManagement, {
      name: hubName,
      webPubsubId: webPubSubResource.id,
      eventHandler: [
        {
          urlTemplate: `https://${functionApp.name}.azurewebsites.net/runtime/webhooks/webpubsub?code=${dataAzurermFunctionAppHostKeys.webpubsubExtensionKey}`,
          systemEvents: ['connect', 'disconnected'],
          userEventPattern: '*',
        },
      ],
      anonymousConnectionsEnabled: true,
      dependsOn: [functionApp, dataAzurermFunctionAppHostKeys, webPubSubResource],
      provider: providerResource,
    })
  }
}
