import { TerraformStack } from 'cdktf'
import {
  ApiManagementApiOperation,
  ApiManagementApiOperationPolicy,
  FunctionApp,
  ResourceGroup,
} from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import * as Mustache from 'mustache'
import { templates } from '../templates'

export class TerraformApiManagementApiOperationPolicy {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    apiManagementApiOperation: ApiManagementApiOperation,
    appPrefix: string,
    environmentName: string,
    functionApp: FunctionApp
  ): ApiManagementApiOperationPolicy {
    const idApiManagementApiOperationPolicy = toTerraformName(appPrefix, 'amaop')
    const policyContent = Mustache.render(templates.policy, { functionAppName: functionApp.name })
    return new ApiManagementApiOperationPolicy(terraformStack, idApiManagementApiOperationPolicy, {
      apiName: apiManagementApiOperation.apiName,
      apiManagementName: apiManagementApiOperation.apiManagementName,
      resourceGroupName: resourceGroup.name,
      operationId: apiManagementApiOperation.operationId,
      xmlContent: policyContent,
    })
  }
}
