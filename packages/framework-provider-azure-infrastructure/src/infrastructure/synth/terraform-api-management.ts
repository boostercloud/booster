import { TerraformStack } from 'cdktf'
import { apiManagement, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { configuration } from '../helper/params'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformApiManagement {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    apiManagementName: string,
    appPrefix: string
  ): apiManagement.ApiManagement {
    const idApiManagement = toTerraformName(appPrefix, 'am')
    return new apiManagement.ApiManagement(terraformStackResource, idApiManagement, {
      name: apiManagementName,
      location: resourceGroupResource.location,
      resourceGroupName: resourceGroupResource.name,
      publisherName: configuration.publisherName,
      publisherEmail: configuration.publisherEmail,
      skuName: 'Consumption_0',
      provider: providerResource,
    })
  }
}
