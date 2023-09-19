import { servicePlan } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformServicePlan {
  static build(
    { terraformStack, azureProvider, appPrefix, resourceGroupName, resourceGroup }: ApplicationSynthStack,
    suffixName: string,
    skuName: string,
    workerCount: number
  ): servicePlan.ServicePlan {
    const id = toTerraformName(appPrefix, suffixName)
    return new servicePlan.ServicePlan(terraformStack, id, {
      name: `${resourceGroupName}${suffixName}`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      osType: 'Windows',
      skuName: skuName,
      workerCount: workerCount,
      provider: azureProvider,
    })
  }
}
