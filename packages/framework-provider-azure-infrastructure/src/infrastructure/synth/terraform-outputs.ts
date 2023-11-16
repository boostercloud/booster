import { TerraformOutput } from 'cdktf'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformOutputs {
  static build(applicationSynthStack: ApplicationSynthStack): void {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    const baseUrl = `https://${applicationSynthStack.resourceGroupName}apis.azure-api.net/${environment}`

    new TerraformOutput(applicationSynthStack.azureProvider, 'httpURL', {
      value: baseUrl,
      description: 'The base URL for all the auth endpoints',
    })
    new TerraformOutput(applicationSynthStack.azureProvider, 'graphqlURL', {
      value: baseUrl + applicationSynthStack.graphQLApiManagementApiOperation?.urlTemplate,
      description: 'The base URL for sending GraphQL mutations and queries',
    })
    new TerraformOutput(applicationSynthStack.azureProvider, 'sensorHealthURL', {
      value: baseUrl + applicationSynthStack.sensorHealthApiManagementApiOperation?.urlTemplate,
      description: 'The base URL for getting health information',
    })

    if (applicationSynthStack.webPubSub) {
      new TerraformOutput(applicationSynthStack.azureProvider, 'websocketURL', {
        value: `wss://${applicationSynthStack.webPubSub.hostname}/client/hubs/${applicationSynthStack.eventHubName}`,
        description: 'The URL for the websocket communication. Used for subscriptions',
      })
    }
  }
}
