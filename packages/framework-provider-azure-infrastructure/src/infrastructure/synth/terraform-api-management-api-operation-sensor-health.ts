import { TerraformStack } from 'cdktf'
import { apiManagementApi, apiManagementApiOperation, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformApiManagementApiOperationSensorHealth {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    group: resourceGroup.ResourceGroup,
    apiManagementApiResource: apiManagementApi.ApiManagementApi,
    appPrefix: string,
    name: string
  ): apiManagementApiOperation.ApiManagementApiOperation {
    const idApiManagementApiOperation = toTerraformName(appPrefix, 'amash' + name[0])
    return new apiManagementApiOperation.ApiManagementApiOperation(
      terraformStackResource,
      idApiManagementApiOperation,
      {
        operationId: `${name}GET`,
        apiName: apiManagementApiResource.name,
        apiManagementName: apiManagementApiResource.apiManagementName,
        resourceGroupName: group.name,
        displayName: '/sensor/health',
        method: 'GET',
        urlTemplate: '/sensor/health/*',
        description: '',
        response: [
          {
            statusCode: 200,
          },
        ],
        provider: providerResource,
      }
    )
  }
}
