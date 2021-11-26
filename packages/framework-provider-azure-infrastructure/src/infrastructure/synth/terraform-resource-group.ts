import { ResourceGroup } from '@cdktf/provider-azurerm'
import { TerraformStack } from 'cdktf'
import { getDeployRegion, toTerraformName } from '../helper/utils'

export class TerraformResourceGroup {
  static build(terraformStack: TerraformStack, appPrefix: string, resourceGroupName: string): ResourceGroup {
    const id = toTerraformName(appPrefix, 'rg')
    return new ResourceGroup(terraformStack, id, {
      name: resourceGroupName,
      location: getDeployRegion(),
    })
  }
}
