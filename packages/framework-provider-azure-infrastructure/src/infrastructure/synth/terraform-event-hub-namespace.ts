import { eventhubNamespace } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformEventHubNamespace {
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    resourceGroup,
    resourceGroupName,
  }: ApplicationSynthStack): eventhubNamespace.EventhubNamespace {
    const idApiManagement = toTerraformName(appPrefix, 'ehn')
    return new eventhubNamespace.EventhubNamespace(terraformStack, idApiManagement, {
      name: `${resourceGroupName}ehn`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      provider: azureProvider,
      sku: 'Basic', // Basic, Standard and Premium. Maximum size of Event Hubs publication: Basic=256K, Standard=1Mb
      capacity: 2, // Throughput Units for a Standard SKU namespace. Default capacity has a maximum of 2, but can be increased in blocks of 2 on a committed purchase basis. Defaults to 1.
      autoInflateEnabled: false,
    })
  }
}
