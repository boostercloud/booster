import { resourceGroup } from '@cdktf/provider-azurerm'
import { TerraformStack } from 'cdktf'
import { getDeployRegion, toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformResourceGroup {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    appPrefix: string,
    resourceGroupName: string
  ): resourceGroup.ResourceGroup {
    const id = toTerraformName(appPrefix, 'rg')
    return new resourceGroup.ResourceGroup(terraformStackResource, id, {
      name: resourceGroupName,
      location: getDeployRegion(),
      provider: providerResource,
    })
  }
}
