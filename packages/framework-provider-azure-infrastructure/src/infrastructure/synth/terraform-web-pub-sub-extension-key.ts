import { TerraformStack } from 'cdktf'
import { dataAzurermFunctionAppHostKeys, resourceGroup, windowsFunctionApp } from '@cdktf/provider-azurerm'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'
import { BoosterConfig } from '@boostercloud/framework-types'
import { TerraformFunctionAppData } from './web-pubsub-extension-key/terraform-function-app-data'
import { TerraformSleep } from './web-pubsub-extension-key/terraform-sleep'

export class TerraformWebPubSubExtensionKey {
  static build(
    config: BoosterConfig,
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    functionAppResource: windowsFunctionApp.WindowsFunctionApp,
    appPrefix: string
  ): dataAzurermFunctionAppHostKeys.DataAzurermFunctionAppHostKeys {
    // Wait for x minutes to give time to Azure to create the webpubsub_extension System Key.
    // Terraform doesn't provide a way to trigger it when the system key is updated
    const sleepResource = TerraformSleep.build(terraformStackResource, appPrefix, [functionAppResource])

    // Return the correct system key created by Azure
    return TerraformFunctionAppData.build(
      providerResource,
      terraformStackResource,
      resourceGroupResource,
      functionAppResource,
      appPrefix,
      sleepResource
    )
  }
}
