import { TerraformStack } from 'cdktf'
import { ApiManagement, ApiManagementApi, FunctionApp, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'

export class TerraformApiManagementApi {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    apiManagement: ApiManagement,
    appPrefix: string,
    environmentName: string,
    functionApp: FunctionApp,
    resourceGroupName: string
  ): ApiManagementApi {
    const idApiManagementApi = toTerraformName(appPrefix, 'amapi')
    return new ApiManagementApi(terraformStack, idApiManagementApi, {
      name: `${resourceGroupName}rest`,
      resourceGroupName: resourceGroup.name,
      apiManagementName: apiManagement.name,
      revision: '1',
      displayName: `${appPrefix}-rest-api`,
      path: environmentName,
      protocols: ['http', 'https'],
      subscriptionRequired: false,
    })
  }
}
