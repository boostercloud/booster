import { apiManagementApi } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformApiManagementApi {
  static build(
    { terraformStack, azureProvider, appPrefix, resourceGroupName, apiManagement }: ApplicationSynthStack,
    environmentName: string
  ): apiManagementApi.ApiManagementApi {
    if (!apiManagement) {
      throw new Error('Undefined apiManagement resource')
    }
    const idApiManagementApi = toTerraformName(appPrefix, 'amapi')
    return new apiManagementApi.ApiManagementApi(terraformStack, idApiManagementApi, {
      name: `${resourceGroupName}rest`,
      resourceGroupName: resourceGroupName,
      apiManagementName: apiManagement.name,
      revision: '1',
      displayName: `${appPrefix}-rest-api`,
      path: environmentName,
      protocols: ['http', 'https'],
      subscriptionRequired: false,
      provider: azureProvider,
    })
  }
}
