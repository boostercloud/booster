import { TerraformStack } from 'cdktf'
import { apiManagementApi, apiManagementApiOperation, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformApiManagementApiOperation {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    group: resourceGroup.ResourceGroup,
    apiManagementApiResource: apiManagementApi.ApiManagementApi,
    appPrefix: string,
    name: string
  ): apiManagementApiOperation.ApiManagementApiOperation {
    const idApiManagementApiOperation = toTerraformName(appPrefix, 'amao' + name[0])
    return new apiManagementApiOperation.ApiManagementApiOperation(
      terraformStackResource,
      idApiManagementApiOperation,
      {
        operationId: `${name}POST`,
        apiName: apiManagementApiResource.name,
        apiManagementName: apiManagementApiResource.apiManagementName,
        resourceGroupName: group.name,
        displayName: `/${name}`,
        method: 'POST',
        urlTemplate: `/${name}`,
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
