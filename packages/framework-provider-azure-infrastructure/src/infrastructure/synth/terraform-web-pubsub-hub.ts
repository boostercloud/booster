import { toTerraformName } from '../helper/utils'
import { webPubsubHub } from '@cdktf/provider-azurerm'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformWebPubsubHub {
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    webPubSub,
    functionApp,
    dataFunctionAppHostKeys,
    webPubSubHubName,
  }: ApplicationSynthStack): webPubsubHub.WebPubsubHub {
    if (!webPubSub) {
      throw new Error('Undefined webPubSub resource')
    }
    if (!functionApp) {
      throw new Error('Undefined functionApp resource')
    }
    if (!dataFunctionAppHostKeys) {
      throw new Error('Undefined dataFunctionAppHostKeys resource')
    }
    const idApiManagement = toTerraformName(appPrefix, 'wpsh')
    const name = webPubSubHubName
    return new webPubsubHub.WebPubsubHub(terraformStack, idApiManagement, {
      name: name,
      webPubsubId: webPubSub.id,
      eventHandler: [
        {
          urlTemplate: `https://${functionApp.name}.azurewebsites.net/runtime/webhooks/webpubsub?code=${dataFunctionAppHostKeys.webpubsubExtensionKey}`,
          systemEvents: ['connect', 'disconnected'],
          userEventPattern: '*',
        },
      ],
      anonymousConnectionsEnabled: true,
      dependsOn: [functionApp, dataFunctionAppHostKeys, webPubSub],
      provider: azureProvider,
    })
  }
}
