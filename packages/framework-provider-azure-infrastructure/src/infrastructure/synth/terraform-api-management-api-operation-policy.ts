import { apiManagementApiOperation, apiManagementApiOperationPolicy } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import * as Mustache from 'mustache'
import { templates } from '../templates'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformApiManagementApiOperationPolicy {
  static build(
    { terraformStack, azureProvider, appPrefix, resourceGroupName, functionApp }: ApplicationSynthStack,
    apiManagementApiOperation: apiManagementApiOperation.ApiManagementApiOperation,
    name: string,
    suffix: string
  ): apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy {
    if (!functionApp) {
      throw new Error('Undefined functionApp resource')
    }
    if (!apiManagementApiOperation) {
      throw new Error('Undefined apiManagementApiOperation resource')
    }
    const idApiManagementApiOperationPolicy = toTerraformName(appPrefix, suffix + name[0])
    const policyContent = Mustache.render(templates.policy, { functionAppName: functionApp.name })
    return new apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy(
      terraformStack,
      idApiManagementApiOperationPolicy,
      {
        apiName: apiManagementApiOperation.apiName,
        apiManagementName: apiManagementApiOperation.apiManagementName,
        resourceGroupName: resourceGroupName,
        operationId: apiManagementApiOperation.operationId,
        xmlContent: policyContent,
        provider: azureProvider,
      }
    )
  }
}
