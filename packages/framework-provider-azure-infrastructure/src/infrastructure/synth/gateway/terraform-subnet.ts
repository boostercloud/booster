import { toTerraformName } from '../../helper/utils'
import { ApplicationSynthStack } from '../../types/application-synth-stack'
import { subnet } from '@cdktf/provider-azurerm'

export class TerraformSubnet {
  static build({
    terraformStack,
    appPrefix,
    virtualNetwork,
    resourceGroupName,
    networkSecurityGroup,
  }: ApplicationSynthStack): subnet.Subnet {
    if (!networkSecurityGroup) {
      throw new Error('Undefined networkSecurityGroup resource')
    }
    if (!virtualNetwork) {
      throw new Error('Undefined virtualNetwork resource')
    }

    return new subnet.Subnet(terraformStack, toTerraformName(appPrefix, 'vsn'), {
      name: `${resourceGroupName}vsn`,
      resourceGroupName: resourceGroupName,
      virtualNetworkName: virtualNetwork.name,
      addressPrefixes: ['10.0.1.0/24'],
    })
  }
}
