import { networkSecurityGroup } from '@cdktf/provider-azurerm'
import { ApplicationSynthStack } from '../../types/application-synth-stack'

export class TerraformNetworkSecurityGroup {
  static build(
    { terraformStack, resourceGroup, resourceGroupName }: ApplicationSynthStack,
    idSG: string,
    name: string
  ): networkSecurityGroup.NetworkSecurityGroup {
    return new networkSecurityGroup.NetworkSecurityGroup(terraformStack, idSG, {
      name: name,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      securityRule: [
        {
          name: 'Allow-All-' + name,
          priority: 100,
          direction: 'Inbound',
          access: 'Allow',
          protocol: 'Tcp',
          sourcePortRange: '*',
          destinationPortRange: '*',
          sourceAddressPrefix: '*',
          destinationAddressPrefix: '*',
        },
        {
          name: 'Allow-All-' + name + 'b',
          priority: 100,
          direction: 'Outbound',
          access: 'Allow',
          protocol: 'Tcp',
          sourcePortRange: '*',
          destinationPortRange: '*',
          sourceAddressPrefix: '*',
          destinationAddressPrefix: '*',
        },
      ],
    })
  }
}
