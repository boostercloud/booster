import { Table } from '@aws-cdk/aws-dynamodb'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { Fn } from '@aws-cdk/core'
import { createPolicyStatement } from './policies'
import { GraphQLStackMembers } from './graphql-stack'
import { EventsStackMembers } from './events-stack'

export const setupPermissions = (
  graphQLStack: GraphQLStackMembers,
  eventsStack: EventsStackMembers,
  readModelTables: Array<Table>,
  websocketAPI: CfnApi
): void => {
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

  const { graphQLLambda, subscriptionsTable, subscriptionDispatcherLambda, connectionsStore } = graphQLStack
  const { eventsLambda, eventsStore } = eventsStack
  graphQLLambda.addToRolePolicy(
    createPolicyStatement([eventsStore.tableArn], ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchWriteItem'])
  )
  graphQLLambda.addToRolePolicy(
    createPolicyStatement(
      [subscriptionsTable.tableArn + '*'], // The '*' at the end is to also grant permissions on table indexes
      ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:DeleteItem', 'dynamodb:BatchWriteItem']
    )
  )
  graphQLLambda.addToRolePolicy(
    createPolicyStatement([connectionsStore.tableArn], ['dynamodb:GetItem', 'dynamodb:PutItem', 'dynamodb:DeleteItem'])
  )
  graphQLLambda.addToRolePolicy(websocketManageConnectionsPolicy)

  subscriptionDispatcherLambda.addToRolePolicy(
    createPolicyStatement([subscriptionsTable.tableArn], ['dynamodb:Query*'])
  )
  subscriptionDispatcherLambda.addToRolePolicy(websocketManageConnectionsPolicy)

  eventsLambda.addToRolePolicy(
    createPolicyStatement([eventsStore.tableArn], ['dynamodb:BatchWriteItem', 'dynamodb:Query*', 'dynamodb:Put*'])
  )

  const tableArns = readModelTables.map((table): string => table.tableArn)
  if (tableArns.length > 0) {
    eventsLambda.addToRolePolicy(createPolicyStatement(tableArns, ['dynamodb:Get*', 'dynamodb:Put*']))
    graphQLLambda.addToRolePolicy(createPolicyStatement(tableArns, ['dynamodb:Query*', 'dynamodb:Scan*']))
  }
}
