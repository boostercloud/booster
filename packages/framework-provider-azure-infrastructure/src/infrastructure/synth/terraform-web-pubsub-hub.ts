import { toTerraformName } from '../helper/utils'
import { webPubsubHub } from '@cdktf/provider-azurerm'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformWebPubsubHub {
  /**
   * Builds the Web PubSub hub with a placeholder URL template.
   * The actual function key will be set after deployment by the updateWebPubSubHub function
   * in index.ts, which runs after the full function ZIP is deployed.
   */
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    webPubSub,
    functionApp,
    webPubSubHubName,
  }: ApplicationSynthStack): webPubsubHub.WebPubsubHub {
    if (!webPubSub) {
      throw new Error('Undefined webPubSub resource')
    }
    if (!functionApp) {
      throw new Error('Undefined functionApp resource')
    }
    const idApiManagement = toTerraformName(appPrefix, 'wpsh')
    const name = webPubSubHubName
    // Create hub with placeholder - will be updated after full ZIP deployment
    return new webPubsubHub.WebPubsubHub(terraformStack, idApiManagement, {
      name: name,
      webPubsubId: webPubSub.id,
      eventHandler: [
        {
          // Placeholder URL - will be updated after deployZip completes
          urlTemplate: `https://${functionApp.name}.azurewebsites.net/runtime/webhooks/webpubsub?code=PLACEHOLDER`,
          systemEvents: ['connect', 'disconnected'],
          userEventPattern: '*',
        },
      ],
      anonymousConnectionsEnabled: true,
      dependsOn: [functionApp, webPubSub],
      provider: azureProvider,
      lifecycle: {
        // Ignore changes to event_handler since it will be managed post-deployment
        ignoreChanges: ['event_handler'],
      },
    })
  }
}
