import { TerraformStack } from 'cdktf'
import {
  apiManagementApiOperation,
  apiManagementApiOperationPolicy,
  resourceGroup,
  windowsFunctionApp,
} from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import * as Mustache from 'mustache'
import { templates } from '../templates'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformApiManagementApiOperationPolicy {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    apiManagementApiOperationResource: apiManagementApiOperation.ApiManagementApiOperation,
    appPrefix: string,
    environmentName: string,
    functionAppResource: windowsFunctionApp.WindowsFunctionApp,
    name: string
  ): apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy {
    const idApiManagementApiOperationPolicy = toTerraformName(appPrefix, 'amaop' + name[0])
    const policyContent = Mustache.render(templates.policy, { functionAppName: functionAppResource.name })
    return new apiManagementApiOperationPolicy.ApiManagementApiOperationPolicy(
      terraformStackResource,
      idApiManagementApiOperationPolicy,
      {
        apiName: apiManagementApiOperationResource.apiName,
        apiManagementName: apiManagementApiOperationResource.apiManagementName,
        resourceGroupName: resourceGroupResource.name,
        operationId: apiManagementApiOperationResource.operationId,
        xmlContent: policyContent,
        provider: providerResource,
      }
    )
  }
}
