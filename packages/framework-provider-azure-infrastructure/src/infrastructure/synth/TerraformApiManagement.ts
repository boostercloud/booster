import { TerraformStack } from 'cdktf'
import {
  ApiManagement,
  ApiManagementApi,
  ApiManagementApiOperation,
  ApiManagementApiOperationPolicy,
  FunctionApp,
  ResourceGroup,
} from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { configuration } from '../helper/params'
import * as Mustache from 'mustache'
import { templates } from '../templates'

export class TerraformApiManagement {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    appPrefix: string,
    environmentName: string,
    functionApp: FunctionApp,
    resourceGroupName: string
  ): ApiManagementApi {
    const idApiManagement = toTerraformName(appPrefix, 'am')
    const apiManagement = new ApiManagement(terraformStack, idApiManagement, {
      name: `${resourceGroupName}apis`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroup.name,
      publisherName: configuration.publisherName,
      publisherEmail: configuration.publisherEmail,
      skuName: 'Consumption_0',
    })

    const idApiManagementApi = toTerraformName(appPrefix, 'amapi')
    const apiManagementApi = new ApiManagementApi(terraformStack, idApiManagementApi, {
      name: `${resourceGroupName}rest`,
      resourceGroupName: resourceGroup.name,
      apiManagementName: apiManagement.name,
      revision: '1',
      displayName: `${appPrefix}-rest-api`,
      path: environmentName,
      protocols: ['http', 'https'],
      subscriptionRequired: false,
    })

    const idApiManagementApiOperation = toTerraformName(appPrefix, 'amao')
    const apiManagementApiOperation = new ApiManagementApiOperation(terraformStack, idApiManagementApiOperation, {
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

    const idApiManagementApiOperationPolicy = toTerraformName(appPrefix, 'amaop')
    const policyContent = Mustache.render(templates.policy, { functionAppName: functionApp.name })
    new ApiManagementApiOperationPolicy(terraformStack, idApiManagementApiOperationPolicy, {
      apiName: apiManagementApiOperation.apiName,
      apiManagementName: apiManagementApiOperation.apiManagementName,
      resourceGroupName: resourceGroup.name,
      operationId: apiManagementApiOperation.operationId,
      xmlContent: policyContent,
    })

    return apiManagementApi
  }
}
