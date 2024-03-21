import { resourceGroup } from '@cdktf/provider-azurerm'
import { getDeployRegion, toTerraformName } from '../helper/utils'
import { StackNames } from '../types/application-synth-stack'

export class TerraformResourceGroup {
  static build({
    terraformStack,
    resourceGroupName,
    azureProvider,
    appPrefix,
  }: StackNames): resourceGroup.ResourceGroup {
    const id = toTerraformName(appPrefix, 'rg')
    return new resourceGroup.ResourceGroup(terraformStack, id, {
      name: resourceGroupName,
      location: getDeployRegion(),
      provider: azureProvider,
    })
  }
}
