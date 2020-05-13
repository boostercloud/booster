import { App, CfnOutput, Fn, Stack, StackProps } from '@aws-cdk/core'
import { Table } from '@aws-cdk/aws-dynamodb'
import { Function } from '@aws-cdk/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'
import { AuthStack } from './auth-stack'
import { EventsStack } from './events-stack'
import { ReadModelsStack } from './read-models-stack'
import { GraphQLStack } from './graphql-stack'
import { RestApi } from '@aws-cdk/aws-apigateway'
import { CfnApi, CfnStage } from '@aws-cdk/aws-apigatewayv2'
import { baseURLForAPI } from '../params'
import { setupPermissions } from './permissions'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig, readonly props?: StackProps) {}

  public buildOn(app: App): void {
    const stack = new Stack(app, this.config.resourceNames.applicationStack, this.props)
    const restAPI = this.buildRootRESTAPI(stack)
    const websocketAPI = this.buildRootWebSocketAPI(stack)
    const apis = {
      restAPI,
      websocketAPI,
    }

    new AuthStack(this.config, stack, apis).build()
    const readModelTables = new ReadModelsStack(this.config, stack).build()
    const graphQLStack = new GraphQLStack(this.config, stack, apis, readModelTables).build()
    const eventsStack = new EventsStack(this.config, stack, apis).build()

    setupPermissions(
      readModelTables,
      graphQLStack.graphQLLambda,
      graphQLStack.subscriptionDispatcherLambda,
      graphQLStack.subscriptionsTable,
      websocketAPI,
      eventsStack.eventsStore,
      eventsStack.eventsLambda
    )
  }

  private buildRootRESTAPI(stack: Stack): RestApi {
    const rootAPI = new RestApi(stack, this.config.resourceNames.applicationStack + '-rest-api', {
      deployOptions: { stageName: this.config.environmentName },
    })

    new CfnOutput(stack, 'httpURL', {
      value: rootAPI.url,
      description: 'The base URL for all the auth endpoints and for sending GraphQL mutations and queries',
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

function setupPermissions(
  readModelTables: Array<Table>,
  graphQLLambda: Function,
  subscriptionDispatcherLambda: Function,
  subscriptionsTable: Table,
  websocketAPI: CfnApi,
  eventsStore: Table,
  eventsLambda: Function
): void {
  graphQLLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [],
      actions: ['dynamodb:Query*', 'dynamodb:Put*'],
    })
  )
  graphQLLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [subscriptionsTable.tableArn],
      actions: ['dynamodb:Put*'],
    })
  )

  subscriptionDispatcherLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [subscriptionsTable.tableArn],
      actions: ['dynamodb:Query*'],
    })
  )
  subscriptionDispatcherLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [
        Fn.join(':', [
          'arn',
          Fn.ref('AWS::Partition'),
          'execute-api',
          Fn.ref('AWS::Region'),
          Fn.ref('AWS::AccountId'),
          `${websocketAPI.ref}/*`,
        ]),
      ],
      actions: ['execute-api:ManageConnections'],
    })
  )

  eventsLambda.addToRolePolicy(
    new PolicyStatement({
      resources: [eventsStore.tableArn],
      actions: ['dynamodb:Query*', 'dynamodb:Put*'],
    })
  )

  const tableArns = readModelTables.map((table): string => table.tableArn)
  if (tableArns.length > 0) {
    eventsLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Get*', 'dynamodb:Put*'],
        resources: tableArns,
      })
    )
    graphQLLambda.addToRolePolicy(
      new PolicyStatement({
        actions: ['dynamodb:Query*', 'dynamodb:Scan*'],
        resources: tableArns,
      })
    )
  }
}
