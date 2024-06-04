import { TerraformOutput } from 'cdktf'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformOutputs {
  static build(applicationSynthStack: ApplicationSynthStack): void {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    const fdqn = applicationSynthStack.publicIPData?.fqdn ?? 'localhost'
    const baseUrl = `http://${fdqn}/${environment}`

    new TerraformOutput(applicationSynthStack.azureProvider, 'httpURL', {
      value: baseUrl,
      description: 'The base URL for all the auth endpoints',
    })
    new TerraformOutput(applicationSynthStack.azureProvider, 'graphqlURL', {
      value: `${baseUrl}/graphql`,
      description: 'The base URL for sending GraphQL mutations and queries',
    })
    new TerraformOutput(applicationSynthStack.azureProvider, 'sensorHealthURL', {
      value: `${baseUrl}/sensor/health`,
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
