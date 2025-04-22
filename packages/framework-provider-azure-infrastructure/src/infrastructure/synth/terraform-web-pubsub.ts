import { toTerraformName } from '../helper/utils'
import { webPubsub } from '@cdktf/provider-azurerm'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformWebPubsub {
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    resourceGroupName,
    resourceGroup,
  }: ApplicationSynthStack): webPubsub.WebPubsub {
    const id = toTerraformName(appPrefix, 'wps')

    return new webPubsub.WebPubsub(terraformStack, id, {
      name: `${resourceGroupName}wps`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      sku: 'Free_F1',
      capacity: 1,
      provider: azureProvider,
    })
  }
}
