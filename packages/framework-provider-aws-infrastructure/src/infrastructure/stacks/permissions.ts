import { Table } from '@aws-cdk/aws-dynamodb'
import { Function } from '@aws-cdk/aws-lambda'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { Stream } from '@aws-cdk/aws-kinesis'
import { Fn } from '@aws-cdk/core'
import { createPolicyStatement } from './policies'

export const setupPermissions = (
  readModelTables: Array<Table>,
  graphQLLambda: Function,
  subscriptionDispatcherLambda: Function,
  subscriptionsTable: Table,
  websocketAPI: CfnApi,
  eventsStream: Stream,
  eventsStore: Table,
  eventsLambda: Function
): void => {
  graphQLLambda.addToRolePolicy(
    createPolicyStatement([eventsStream.streamArn], ['kinesis:Put*', 'dynamodb:Query*', 'dynamodb:Put*'])
  )
  graphQLLambda.addToRolePolicy(createPolicyStatement([subscriptionsTable.tableArn], ['dynamodb:Put*']))

  subscriptionDispatcherLambda.addToRolePolicy(
    createPolicyStatement([subscriptionsTable.tableArn], ['dynamodb:Query*'])
  )
  subscriptionDispatcherLambda.addToRolePolicy(
    createPolicyStatement(
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
  )

  eventsLambda.addToRolePolicy(createPolicyStatement([eventsStore.tableArn], ['dynamodb:Query*', 'dynamodb:Put*']))

  const tableArns = readModelTables.map((table): string => table.tableArn)
  if (tableArns.length > 0) {
    eventsLambda.addToRolePolicy(createPolicyStatement(tableArns, ['dynamodb:Get*', 'dynamodb:Put*']))
    graphQLLambda.addToRolePolicy(createPolicyStatement(tableArns, ['dynamodb:Query*', 'dynamodb:Scan*']))
  }
}
