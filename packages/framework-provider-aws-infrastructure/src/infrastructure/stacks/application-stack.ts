import { App, CfnOutput, Stack, StackProps } from '@aws-cdk/core'
import * as dynamodb from '@aws-cdk/aws-dynamodb'
import { Function } from '@aws-cdk/aws-lambda'
import { Stream } from '@aws-cdk/aws-kinesis'
import { BoosterConfig } from '@boostercloud/framework-types'
import { PolicyStatement } from '@aws-cdk/aws-iam'
import { RestAPIStack } from './rest-api-stack'
import { AuthStack } from './auth-stack'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'
import { GraphQLStack } from "./graphql-stack";
import { RestApi } from "@aws-cdk/aws-apigateway";
import { CfnApi, CfnStage } from '@aws-cdk/aws-apigatewayv2'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig, readonly props?: StackProps) {}

  public buildOn(app: App): void {
    const stack = new Stack(app, this.config.resourceNames.applicationStack, this.props)
    const restAPI = this.buildRootRESTAPI(stack)
    const websocketAPI = this.buildRootWebSocketAPI(stack)

    new AuthStack(this.config, stack, restAPI).build()
    const readModelTables = new ReadModelsStack(this.config, stack).build()
    const graphQLStack = new GraphQLStack(this.config, stack, restAPI, websocketAPI).build()
    const eventsStack = new EventsStack(this.config, stack).build()

    // Deprecated
    const restAPIStack = new RestAPIStack(this.config, stack, restAPI).build()

    setupPermissions(
      readModelTables,
      restAPIStack.commandsLambda,
      eventsStack.eventsLambda,
      restAPIStack.readModelFetcherLambda,
      graphQLStack.graphQLLambda,
      eventsStack.eventsStream,
      eventsStack.eventsStore,
      eventsStack.eventsLambda
    )
  }

  private buildRootRESTAPI(stack: Stack): RestApi {
    const rootAPI = new RestApi(stack, this.config.resourceNames.applicationStack + '-rest-api')

    new CfnOutput(stack, 'base-REST-URL', {
      value: rootAPI.url,
      description: 'The base URL for all the REST the endpoints of your application',
    })

    return rootAPI
  }

  private buildRootWebSocketAPI(stack: Stack): CfnApi {
    const localID = this.config.resourceNames.applicationStack + '-graphql-api'
    const rootAPI = new CfnApi(stack, localID, {
      name: localID,
      protocolType: 'WEBSOCKET',
      routeSelectionExpression: '$request.body.action',
    })
    const stage = new CfnStage(stack, localID + '-stage', {
      apiId: rootAPI.ref,
      autoDeploy: true,
      stageName: 'dev',
    })
    stage.addDependsOn(rootAPI)

    new CfnOutput(stack, 'base-websocket-URL', {
      value: baseURLForWebsocketStage(stage),
      description: 'The URL for the websocket communication. Used for subscriptions',
    })

    return rootAPI
  }
}

function setupPermissions(
  readModelTables: Array<dynamodb.Table>,
  commandsLambda: Function,
  readModelsLambda: Function,
  readModelFetcherLambda: Function,
  graphQLLambda: Function,
  eventsStream: Stream,
  eventsStore: dynamodb.Table,
  eventsLambda: Function
): void {
  // The command dispatcher can send events to the event stream
  commandsLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStream.streamArn],
      actions: ['kinesis:Put*', 'dynamodb:Query*', 'dynamodb:Put*'],
    })
  )

  readModelsLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStore.tableArn],
      actions: ['dynamodb:Query*', 'dynamodb:Put*'],
    })
  )

  graphQLLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStore.tableArn],
      actions: ['dynamodb:Query*', 'dynamodb:Get*'],
    })
  )

  const tableArns = readModelTables.map((table): string => table.tableArn)
  if (tableArns.length > 0) {
    readModelFetcherLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Get*', 'dynamodb:Scan*'],
        resources: tableArns,
      })
    )
    eventsLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Get*', 'dynamodb:Put*'],
        resources: tableArns,
      })
    )
    readModelsLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Put*'],
        resources: tableArns,
      })
    )
  }
}

function baseURLForWebsocketStage(stage: CfnStage): string {
  return `wss://${stage.apiId}.execute-api.${stage.stack.region}.${stage.stack.urlSuffix}/${stage.stageName}/`
}
