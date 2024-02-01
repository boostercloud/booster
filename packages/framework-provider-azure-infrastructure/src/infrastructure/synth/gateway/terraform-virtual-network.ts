import { virtualNetwork } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../../helper/utils'
import { ApplicationSynthStack } from '../../types/application-synth-stack'

export class TerraformVirtualNetwork {
  static build({
    terraformStack,
    appPrefix,
    resourceGroup,
    resourceGroupName,
    networkSecurityGroup,
  }: ApplicationSynthStack): virtualNetwork.VirtualNetwork {
    if (!networkSecurityGroup) {
      throw new Error('Undefined networkSecurityGroup resource')
    }
    return new virtualNetwork.VirtualNetwork(terraformStack, toTerraformName(appPrefix, 'vn'), {
      name: `${resourceGroupName}vn`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      addressSpace: ['10.0.0.0/16'],
    })
  }
}
