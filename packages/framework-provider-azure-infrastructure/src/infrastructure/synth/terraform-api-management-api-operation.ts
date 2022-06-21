import { TerraformStack } from 'cdktf'
import { ApiManagementApi, ApiManagementApiOperation, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'

export class TerraformApiManagementApiOperation {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    apiManagementApi: ApiManagementApi,
    appPrefix: string
  ): ApiManagementApiOperation {
    const idApiManagementApiOperation = toTerraformName(appPrefix, 'amao')
    return new ApiManagementApiOperation(terraformStack, idApiManagementApiOperation, {
      operationId: 'graphqlPOST',
      apiName: apiManagementApi.name,
      apiManagementName: apiManagementApi.apiManagementName,
      resourceGroupName: resourceGroup.name,
      displayName: '/graphql',
      method: 'POST',
      urlTemplate: '/graphql',
      description: '',
      response: [
        {
          statusCode: 200,
        },
      ],
    })
  }
}
