import { Table } from '@aws-cdk/aws-dynamodb'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { Fn } from '@aws-cdk/core'
import { createPolicyStatement } from './policies'
import { GraphQLStackMembers } from './graphql-stack'
import { ScheduledCommandStackMembers } from './scheduled-commands-stack'
import { EventsStackMembers } from './events-stack'
import { Function } from '@aws-cdk/aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'

export const setupPermissions = (
  config: BoosterConfig,
  graphQLStack: GraphQLStackMembers,
  eventsStack: EventsStackMembers,
  readModelTables: Array<Table>,
  websocketAPI?: CfnApi,
  scheduledCommandStack?: ScheduledCommandStackMembers
): void => {
  if (config.enableSubscriptions && !websocketAPI) {
    throw new Error('WebsocketAPI undefined with enableSubscriptions enabled')
  }
  const { graphQLLambda, subscriptionsStore, subscriptionNotifier, connectionsStore } = graphQLStack
  const { eventsLambda, eventsStore } = eventsStack
  const scheduledLambda = scheduledCommandStack?.scheduledLambda
  if (websocketAPI) {
    const websocketManageConnectionsPolicy = createPolicyStatement(
      [
        Fn.join(':', [
          'arn',
          Fn.ref('AWS::Partition'),
          'execute-api',
          Fn.ref('AWS::Region'),
          Fn.ref('AWS::AccountId'),
          `${websocketAPI.ref}/*`,
        ]),
      ],
      ['execute-api:ManageConnections']
    )
    if (subscriptionsStore && connectionsStore && subscriptionNotifier) {
      grantFullAccessToSubscriptionsStore(subscriptionsStore, graphQLLambda)
      graphQLLambda.addToRolePolicy(
        createPolicyStatement(
          [connectionsStore.tableArn],
          ['dynamodb:GetItem', 'dynamodb:PutItem', 'dynamodb:DeleteItem']
        )
      )
      graphQLLambda.addToRolePolicy(websocketManageConnectionsPolicy)
      // Subscription notifier lambda permissions
      subscriptionNotifier.addToRolePolicy(createPolicyStatement([subscriptionsStore.tableArn], ['dynamodb:Query*']))
      subscriptionNotifier.addToRolePolicy(websocketManageConnectionsPolicy)
    }
  }

  // GraphQL Lambda permissions
  grantFullAccessToEventStore(eventsStore, graphQLLambda)
  grantReadAccessToReadModels(readModelTables, graphQLLambda)

  // Events Lambda permissions
  grantFullAccessToEventStore(eventsStore, eventsLambda)
  grantFullAccessToReadModels(readModelTables, eventsLambda)

  // Scheduled lambda permissions
  if (scheduledLambda) {
    grantFullAccessToEventStore(eventsStore, scheduledLambda)
    grantReadAccessToReadModels(readModelTables, scheduledLambda)
  }
}

function grantFullAccessToEventStore(store: Table, lambda: Function): void {
  lambda.addToRolePolicy(
    createPolicyStatement(
      [store.tableArn],
      ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchGetItem', 'dynamodb:BatchWriteItem']
    )
  )
  lambda.addToRolePolicy(
    createPolicyStatement(
      [store.tableArn + '*'], // The '*' at the end is to grant permissions on table indexes (only read permissions)
      ['dynamodb:Query*']
    )
  )
}

function grantFullAccessToSubscriptionsStore(store: Table, lambda: Function): void {
  lambda.addToRolePolicy(
    createPolicyStatement(
      [store.tableArn],
      ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:DeleteItem', 'dynamodb:BatchWriteItem']
    )
  )
  lambda.addToRolePolicy(
    createPolicyStatement(
      [store.tableArn + '*'], // The '*' at the end is to grant permissions on table indexes (only read permissions)
      ['dynamodb:Query*']
    )
  )
}

function grantFullAccessToReadModels(readModelTables: Array<Table>, lambda: Function): void {
  const tableARNs = readModelTables.map((table): string => table.tableArn)
  if (tableARNs.length > 0) {
    lambda.addToRolePolicy(
      createPolicyStatement(tableARNs, [
        'dynamodb:Get*',
        'dynamodb:Query*',
        'dynamodb:Scan*',
        'dynamodb:Put*',
        'dynamodb:DeleteItem*',
      ])
    )
  }
}

function grantReadAccessToReadModels(readModelTables: Array<Table>, lambda: Function): void {
  const tableARNs = readModelTables.map((table): string => table.tableArn)
  if (tableARNs.length > 0) {
    lambda.addToRolePolicy(createPolicyStatement(tableARNs, ['dynamodb:Query*', 'dynamodb:Scan*']))
  }
}
