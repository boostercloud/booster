import { dataAzurermPublicIp } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../../helper/utils'
import { ApplicationSynthStack } from '../../types/application-synth-stack'

export class TerraformPublicIpData {
  static build({
    terraformStack,
    appPrefix,
    resourceGroupName,
    azureProvider,
    publicIP,
  }: ApplicationSynthStack): dataAzurermPublicIp.DataAzurermPublicIp {
    if (!publicIP) {
      throw new Error('Undefined publicIP resource')
    }

    const id = toTerraformName(appPrefix, 'datapip')
    return new dataAzurermPublicIp.DataAzurermPublicIp(terraformStack, id, {
      name: publicIP.name,
      resourceGroupName: resourceGroupName,
      provider: azureProvider,
    })
  }
}
