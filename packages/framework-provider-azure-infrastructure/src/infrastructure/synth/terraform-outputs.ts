import { TerraformOutput, TerraformStack } from 'cdktf'
import { apiManagementApiOperation, resourceGroup, webPubsub } from '@cdktf/provider-azurerm'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformOutputs {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    appPrefix: string,
    resourceGroupResource: resourceGroup.ResourceGroup,
    graphQLApiManagementApiOperationResource: apiManagementApiOperation.ApiManagementApiOperation,
    sensorHealthApiManagementApiOperationResource: apiManagementApiOperation.ApiManagementApiOperation,
    hubName: string,
    webPubsubResource?: webPubsub.WebPubsub
  ): void {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    const baseUrl = `https://${resourceGroupResource.name}apis.azure-api.net/${environment}`

    new TerraformOutput(providerResource, 'httpURL', {
      value: baseUrl,
      description: 'The base URL for all the auth endpoints',
    })
    new TerraformOutput(providerResource, 'graphqlURL', {
      value: baseUrl + graphQLApiManagementApiOperationResource.urlTemplate,
      description: 'The base URL for sending GraphQL mutations and queries',
    })
    new TerraformOutput(providerResource, 'sensorHealthURL', {
      value: baseUrl + sensorHealthApiManagementApiOperationResource.urlTemplate,
      description: 'The base URL for getting health information',
    })

    if (webPubsubResource) {
      new TerraformOutput(providerResource, 'websocketURL', {
        value: `wss://${webPubsubResource.hostname}/client/hubs/${hubName}`,
        description: 'The URL for the websocket communication. Used for subscriptions',
      })
    }
  }
}
