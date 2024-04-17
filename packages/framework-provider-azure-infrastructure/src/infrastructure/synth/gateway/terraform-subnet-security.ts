import { toTerraformName } from '../../helper/utils'
import { ApplicationSynthStack } from '../../types/application-synth-stack'
import { subnetNetworkSecurityGroupAssociation } from '@cdktf/provider-azurerm'

export class TerraformSubnetSecurity {
  static build({
    terraformStack,
    appPrefix,
    networkSecurityGroup,
    subnet,
  }: ApplicationSynthStack): subnetNetworkSecurityGroupAssociation.SubnetNetworkSecurityGroupAssociation {
    if (!networkSecurityGroup) {
      throw new Error('Undefined networkSecurityGroup resource')
    }
    if (!subnet) {
      throw new Error('Undefined subnet resource')
    }

    return new subnetNetworkSecurityGroupAssociation.SubnetNetworkSecurityGroupAssociation(
      terraformStack,
      toTerraformName(appPrefix, 'snsg'),
      {
        subnetId: subnet.id,
        networkSecurityGroupId: networkSecurityGroup.id,
      }
    )
  }
}
