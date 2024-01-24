import { apiManagementApiOperation } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformApiManagementApiOperationSensorHealth {
  static build(
    { terraformStack, azureProvider, appPrefix, resourceGroupName, apiManagementApi }: ApplicationSynthStack,
    name: string
  ): apiManagementApiOperation.ApiManagementApiOperation {
    if (!apiManagementApi) {
      throw new Error('Undefined apiManagementApi resource')
    }
    const idApiManagementApiOperation = toTerraformName(appPrefix, 'amash' + name[0])
    return new apiManagementApiOperation.ApiManagementApiOperation(terraformStack, idApiManagementApiOperation, {
      operationId: `${name}GET`,
      apiName: apiManagementApi.name,
      apiManagementName: apiManagementApi.apiManagementName,
      resourceGroupName: resourceGroupName,
      displayName: '/sensor/health',
      method: 'GET',
      urlTemplate: '/sensor/health/*',
      description: '',
      response: [
        {
          statusCode: 200,
        },
      ],
      provider: azureProvider,
    })
  }
}
