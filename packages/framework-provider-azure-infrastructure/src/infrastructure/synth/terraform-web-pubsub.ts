import { TerraformStack } from 'cdktf'
import { toTerraformName } from '../helper/utils'
import { resourceGroup, webPubsub } from '@cdktf/provider-azurerm'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformWebPubsub {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    appPrefix: string
  ): webPubsub.WebPubsub {
    const id = toTerraformName(appPrefix, 'wps')

    return new webPubsub.WebPubsub(terraformStackResource, id, {
      name: `${resourceGroupResource.name}wps`,
      location: resourceGroupResource.location,
      resourceGroupName: resourceGroupResource.name,
      sku: 'Free_F1',
      capacity: 1,
      provider: providerResource,
    })
  }
}
