import { apiManagement } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { configuration } from '../helper/params'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformApiManagement {
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    resourceGroup,
    resourceGroupName,
    apiManagementName,
  }: ApplicationSynthStack): apiManagement.ApiManagement {
    const idApiManagement = toTerraformName(appPrefix, 'am')
    return new apiManagement.ApiManagement(terraformStack, idApiManagement, {
      name: apiManagementName,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      publisherName: configuration.publisherName,
      publisherEmail: configuration.publisherEmail,
      skuName: 'Consumption_0',
      provider: azureProvider,
    })
  }
}
