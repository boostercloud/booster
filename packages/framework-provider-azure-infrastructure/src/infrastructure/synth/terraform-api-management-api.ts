import { TerraformStack } from 'cdktf'
import { apiManagement, apiManagementApi, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformApiManagementApi {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    apiManagementResource: apiManagement.ApiManagement,
    appPrefix: string,
    environmentName: string,
    resourceGroupName: string
  ): apiManagementApi.ApiManagementApi {
    const idApiManagementApi = toTerraformName(appPrefix, 'amapi')
    return new apiManagementApi.ApiManagementApi(terraformStackResource, idApiManagementApi, {
      name: `${resourceGroupName}rest`,
      resourceGroupName: resourceGroupResource.name,
      apiManagementName: apiManagementResource.name,
      revision: '1',
      displayName: `${appPrefix}-rest-api`,
      path: environmentName,
      protocols: ['http', 'https'],
      subscriptionRequired: false,
      provider: providerResource,
    })
  }
}
