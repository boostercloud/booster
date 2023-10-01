import { TerraformStack } from 'cdktf'
import { servicePlan, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformServicePlan {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    group: resourceGroup.ResourceGroup,
    appPrefix: string,
    resourceGroupName: string
  ): servicePlan.ServicePlan {
    const id = toTerraformName(appPrefix, 'hpn')
    return new servicePlan.ServicePlan(terraformStackResource, id, {
      name: `${resourceGroupName}hpn`,
      location: group.location,
      resourceGroupName: group.name,
      osType: 'Windows',
      skuName: 'Y1',
      provider: providerResource,
    })
  }
}
