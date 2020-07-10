import { Table } from '@aws-cdk/aws-dynamodb'
import { Function } from '@aws-cdk/aws-lambda'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { Fn } from '@aws-cdk/core'
import { createPolicyStatement } from './policies'

export const setupPermissions = (
  readModelTables: Array<Table>,
  graphQLLambda: Function,
  subscriptionDispatcherLambda: Function,
  subscriptionsTable: Table,
  websocketAPI: CfnApi,
  eventsStore: Table,
  eventsLambda: Function
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
  graphQLLambda.addToRolePolicy(
    createPolicyStatement([eventsStore.tableArn], ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchWriteItem'])
  )
  graphQLLambda.addToRolePolicy(
    createPolicyStatement(
      [subscriptionsTable.tableArn + '*'], // The '*' at the end is to also grant permissions on table indexes
      ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:DeleteItem', 'dynamodb:BatchWriteItem']
    )
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
