import { TerraformStack } from 'cdktf'
import { ApiManagement, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { configuration } from '../helper/params'

export class TerraformApiManagement {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    apiManagementName: string,
    appPrefix: string
  ): ApiManagement {
    const idApiManagement = toTerraformName(appPrefix, 'am')
    return new ApiManagement(terraformStack, idApiManagement, {
      name: apiManagementName,
      location: resourceGroup.location,
      resourceGroupName: resourceGroup.name,
      publisherName: configuration.publisherName,
      publisherEmail: configuration.publisherEmail,
      skuName: 'Consumption_0',
    })
  }
}
