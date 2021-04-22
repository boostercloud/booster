import { App, CfnOutput, Stack, StackProps } from '@aws-cdk/core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { AuthStack } from './auth-stack'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'
import { GraphQLStack } from './graphql-stack'
import { ScheduledCommandStack } from './scheduled-commands-stack'
import { RestApi } from '@aws-cdk/aws-apigateway'
import { CfnApi, CfnStage } from '@aws-cdk/aws-apigatewayv2'
import { baseURLForAPI } from '../params'
import { setupPermissions } from './permissions'
import { InfrastructureRocket } from '../../rockets/infrastructure-rocket'

// Extracted this here to be able to mock it, it's surprisingly hard to mock a constructor!
function buildStack(app: App, applicationStack: string, props?: StackProps): Stack {
  return new Stack(app, applicationStack, props)
}

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig, readonly props?: StackProps) {}

  public buildOn(app: App, rockets?: InfrastructureRocket[]): void {
    const stack = buildStack(app, this.config.resourceNames.applicationStack, this.props)
    const restAPI = this.buildRootRESTAPI(stack)
    const websocketAPI = this.buildRootWebSocketAPI(stack)
    const apis = {
      restAPI,
      websocketAPI,
    }

    const userPool = new AuthStack(this.config, stack, apis).build()
    const readModelTables = new ReadModelsStack(this.config, stack).build()
    const graphQLStack = new GraphQLStack(this.config, stack, apis, readModelTables, userPool).build()
    const scheduledCommandStack = new ScheduledCommandStack(this.config, stack, apis).build()
    const eventsStack = new EventsStack(this.config, stack, apis).build()

    setupPermissions(graphQLStack, eventsStack, readModelTables, websocketAPI, scheduledCommandStack)

    // Load rockets
    rockets?.forEach((rocket) => rocket.mountStack(stack, this.config))
  }

  private buildRootRESTAPI(stack: Stack): RestApi {
    const rootAPI = new RestApi(stack, this.config.resourceNames.applicationStack + '-rest-api', {
      deployOptions: { stageName: this.config.environmentName },
    })

    new CfnOutput(stack, 'httpURL', {
      value: rootAPI.url,
      description: 'The base URL for all the auth endpoints',
    })

    new CfnOutput(stack, 'graphqlURL', {
      value: rootAPI.url + 'graphql',
      description: 'The base URL for sending GraphQL mutations and queries',
    })

    return rootAPI
  }

  private buildRootWebSocketAPI(stack: Stack): CfnApi {
    const localID = this.config.resourceNames.applicationStack + '-websocket-api'
    const rootAPI = new CfnApi(stack, localID, {
      name: localID,
      protocolType: 'WEBSOCKET',
      routeSelectionExpression: '$request.body.action',
    })
    const stage = new CfnStage(stack, localID + '-stage', {
      apiId: rootAPI.ref,
      autoDeploy: true,
      stageName: this.config.environmentName,
    })
    stage.addDependsOn(rootAPI)

    new CfnOutput(stack, 'websocketURL', {
      value: baseURLForAPI(this.config, stack, rootAPI.ref, 'wss'),
      description: 'The URL for the websocket communication. Used for subscriptions',
    })

    return rootAPI
  }
}
