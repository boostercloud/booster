import { dataAzurermFunctionAppHostKeys } from '@cdktf/provider-azurerm'
import { TerraformFunctionAppData } from './web-pubsub-extension-key/terraform-function-app-data'
import { TerraformSleep } from './web-pubsub-extension-key/terraform-sleep'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformWebPubSubExtensionKey {
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    resourceGroup,
    functionApp,
  }: ApplicationSynthStack): dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys {
    if (!functionApp) {
      throw new Error('Undefined functionApp resource')
    }
    // Wait for x minutes to give time to Azure to create the webpubsub_extension System Key.
    // Terraform doesn't provide a way to trigger it when the system key is updated
    const sleepResource = TerraformSleep.build(terraformStack, appPrefix, [functionApp])

    // Return the correct system key created by Azure
    return TerraformFunctionAppData.build(
      azureProvider,
      terraformStack,
      resourceGroup,
      functionApp,
      appPrefix,
      sleepResource
    )
  }
}
