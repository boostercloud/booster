import { publicIp } from '@cdktf/provider-azurerm'
import { ApplicationSynthStack } from '../../types/application-synth-stack'

export class TerraformPublicIp {
  static build(
    { terraformStack, resourceGroup, resourceGroupName, domainNameLabel }: ApplicationSynthStack,
    idPIP: string
  ) {
    return new publicIp.PublicIp(terraformStack, idPIP, {
      name: `${resourceGroupName}pip`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      allocationMethod: 'Static',
      sku: 'Standard',
      domainNameLabel: domainNameLabel,
    })
  }
}
