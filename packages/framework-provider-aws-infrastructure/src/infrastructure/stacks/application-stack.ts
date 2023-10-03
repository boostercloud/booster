import { App, CfnOutput, Stack } from 'aws-cdk-lib'
import { BoosterConfig } from '@boostercloud/framework-types'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'
import { GraphQLStack } from './graphql-stack'
import { ScheduledCommandStack } from './scheduled-commands-stack'
import { RestApi } from 'aws-cdk-lib/aws-apigateway'
import { CfnApi, CfnStage } from 'aws-cdk-lib/aws-apigatewayv2'
import { APIs, baseURLForAPI } from '../params'
import { setupPermissions } from './permissions'
import { InfrastructureRocket } from '../../rockets/infrastructure-rocket'

export class ApplicationStack extends Stack {
  public constructor(readonly config: BoosterConfig, app: App, rockets?: InfrastructureRocket[]) {
    super(app, config.resourceNames.applicationStack)

    const restAPI = this.buildRootRESTAPI()
    const apis: APIs = {
      restAPI,
    }
    if (this.config.enableSubscriptions) {
      apis.websocketAPI = this.buildRootWebSocketAPI()
    }

    const readModelTables = new ReadModelsStack(this.config, this).build()
    const graphQLStack = new GraphQLStack(this.config, this, apis, readModelTables).build()
    const scheduledCommandStack = new ScheduledCommandStack(this.config, this, apis).build()
    const eventsStack = new EventsStack(this.config, this, apis).build()

    setupPermissions(this.config, graphQLStack, eventsStack, readModelTables, apis.websocketAPI, scheduledCommandStack)

    // Load rockets
    rockets?.forEach((rocket) => rocket.mountStack(this, this.config))
  }

  private buildRootRESTAPI(): RestApi {
    const rootAPI = new RestApi(this, this.config.resourceNames.applicationStack + '-rest-api', {
      deployOptions: { stageName: this.config.environmentName },
    })

    new CfnOutput(this, 'httpURL', {
      value: rootAPI.url,
      description: 'The base URL for all the auth endpoints',
    })

    new CfnOutput(this, 'graphqlURL', {
      value: rootAPI.url + 'graphql',
      description: 'The base URL for sending GraphQL mutations and queries',
    })

    return rootAPI
  }

  private buildRootWebSocketAPI(): CfnApi {
    const localID = this.config.resourceNames.applicationStack + '-websocket-api'
    const rootAPI = new CfnApi(this, localID, {
      name: localID,
      protocolType: 'WEBSOCKET',
      routeSelectionExpression: '$request.body.action',
    })
    const stage = new CfnStage(this, localID + '-stage', {
      apiId: rootAPI.ref,
      autoDeploy: true,
      stageName: this.config.environmentName,
    })
    stage.addDependency(rootAPI)

    new CfnOutput(this, 'websocketURL', {
      value: baseURLForAPI(this.config, this, rootAPI.ref, 'wss'),
      description: 'The URL for the websocket communication. Used for subscriptions',
    })

    return rootAPI
  }
}
