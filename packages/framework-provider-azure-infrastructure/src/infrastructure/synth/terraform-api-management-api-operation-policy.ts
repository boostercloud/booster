import { apiManagementApiOperationPolicy } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import * as Mustache from 'mustache'
import { templates } from '../templates'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformApiManagementApiOperationPolicy {
  static build(
    {
      terraformStack,
      azureProvider,
      appPrefix,
      resourceGroupName,
      functionApp,
      graphQLApiManagementApiOperation,
    }: ApplicationSynthStack,
    name: string
  ): apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy {
    if (!functionApp) {
      throw new Error('Undefined functionApp resource')
    }
    if (!graphQLApiManagementApiOperation) {
      throw new Error('Undefined graphQLApiManagementApiOperation resource')
    }
    const idApiManagementApiOperationPolicy = toTerraformName(appPrefix, 'amaop' + name[0])
    const policyContent = Mustache.render(templates.policy, { functionAppName: functionApp.name })
    return new apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy(
      terraformStack,
      idApiManagementApiOperationPolicy,
      {
        apiName: graphQLApiManagementApiOperation.apiName,
        apiManagementName: graphQLApiManagementApiOperation.apiManagementName,
        resourceGroupName: resourceGroupName,
        operationId: graphQLApiManagementApiOperation.operationId,
        xmlContent: policyContent,
        provider: azureProvider,
      }
    )
  }
}
