import { TerraformStack } from 'cdktf'
import { AppServicePlan, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'

export class TerraformApplicationServicePlan {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    appPrefix: string,
    resourceGroupName: string
  ): AppServicePlan {
    const id = toTerraformName(appPrefix, 'hpn')
    return new AppServicePlan(terraformStack, id, {
      name: `${resourceGroupName}hpn`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroup.name,
      kind: 'Windows',
      reserved: false,
      sku: {
        tier: 'Dynamic',
        size: 'Y1',
        capacity: 0,
      },
    })
  }
}
