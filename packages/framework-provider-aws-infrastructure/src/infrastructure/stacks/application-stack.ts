import { BoosterConfig } from '@boostercloud/framework-types'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'
import { GraphQLStack } from './graphql-stack'
import { ScheduledCommandStack } from './scheduled-commands-stack'
import { RestApi } from '@aws-cdk/aws-apigateway'
import { CfnApi, CfnStage } from '@aws-cdk/aws-apigatewayv2'
import { baseURLForAPI } from '../params'
import { setupPermissions } from './permissions'
import { AWSProviderContext } from '../provider-context/aws-provider-context'
import { CfnOutput } from '@aws-cdk/core'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig) {}

  public buildOn(awsProviderContext: AWSProviderContext): void {
    const restAPI = this.buildRootRESTAPI(awsProviderContext)
    const websocketAPI = this.buildRootWebSocketAPI(awsProviderContext)
    const apis = {
      restAPI,
      websocketAPI,
    }

    const readModelTables = new ReadModelsStack(this.config, awsProviderContext.stack).build()
    const graphQLStack = new GraphQLStack(this.config, awsProviderContext.stack, apis, readModelTables).build()
    const scheduledCommandStack = new ScheduledCommandStack(this.config, awsProviderContext.stack, apis).build()
    const eventsStack = new EventsStack(this.config, awsProviderContext.stack, apis).build()

    setupPermissions(graphQLStack, eventsStack, readModelTables, websocketAPI, scheduledCommandStack)
  }

  private buildRootRESTAPI(awsProviderContext: AWSProviderContext): RestApi {
    const rootAPI = new RestApi(awsProviderContext.stack, this.config.resourceNames.applicationStack + '-rest-api', {
      deployOptions: { stageName: this.config.environmentName },
    })

    new CfnOutput(awsProviderContext.stack, 'httpURL', {
      value: rootAPI.url,
      description: 'The base URL for all the auth endpoints',
    })

    new CfnOutput(awsProviderContext.stack, 'graphqlURL', {
      value: rootAPI.url + 'graphql',
      description: 'The base URL for sending GraphQL mutations and queries',
    })

    return rootAPI
  }

  private buildRootWebSocketAPI(awsProviderContext: AWSProviderContext): CfnApi {
    const localID = this.config.resourceNames.applicationStack + '-websocket-api'
    const rootAPI = new CfnApi(awsProviderContext.stack, localID, {
      name: localID,
      protocolType: 'WEBSOCKET',
      routeSelectionExpression: '$request.body.action',
    })
    const stage = new CfnStage(awsProviderContext.stack, localID + '-stage', {
      apiId: rootAPI.ref,
      autoDeploy: true,
      stageName: this.config.environmentName,
    })
    stage.addDependsOn(rootAPI)

    new CfnOutput(awsProviderContext.stack, 'websocketURL', {
      value: baseURLForAPI(this.config, awsProviderContext.stack, rootAPI.ref, 'wss'),
      description: 'The URL for the websocket communication. Used for subscriptions',
    })

    return rootAPI
  }
}
