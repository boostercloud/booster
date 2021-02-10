import { Stack, Duration, CfnOutput } from '@aws-cdk/core'
import {Function, Runtime, Code, EventSourceMapping} from '@aws-cdk/aws-lambda'
import { LambdaRestApi } from '@aws-cdk/aws-apigateway'
import { Table } from '@aws-cdk/aws-dynamodb'
import { PolicyStatement } from '@aws-cdk/aws-iam'
import { Queue } from '@aws-cdk/aws-sqs'
import { BackupStackParams } from './types'
import * as path from 'path'
import { BoosterConfig } from '@boostercloud/framework-types'

export const applyPointInTimeRecoveryBackup = (
  stack: Stack,
  params: BackupStackParams,
  config: BoosterConfig,
  tables: Array<Table>
): void => {
  tables.map((table: Table) => {
    table.node['host'].table.pointInTimeRecoverySpecification = {
      pointInTimeRecoveryEnabled: true,
    }
  })

  if (params.pointInTimeRules?.generateRestoreAPI) {
    createRestoreAPI(stack, config, tables)
  }
}

export const createRestoreAPI = (stack: Stack, config: BoosterConfig, tables: Array<Table>): void => {
  // __dirname is /dist/utils and we need /dist/lambda
  const lambdaDir = __dirname.split('/')
  lambdaDir.pop()

  const queue = new Queue(stack, 'RestoreBackup-Queue', {
    queueName: 'restore-backup-queue',
    deliveryDelay: Duration.seconds(5) // TODO: INCREASE TO 5-15min
  })

  const backend = new Function(stack, 'RestoreBackup-Backend', {
    runtime: Runtime.NODEJS_12_X,
    handler: 'point-in-time-restore-backend.handler',
    code: Code.fromAsset(path.join(lambdaDir.join('/'), 'lambda')),
    timeout: Duration.minutes(1),
    memorySize: 1024,
    environment: {
      TABLE_NAMES: tables.map((table) => table.tableName).join(','),
      APP_NAME: config.appName,
      SQS_URL: queue.queueUrl,
    },
  })

  const listener = new Function(stack, 'RestoreBackup-Listener', {
    runtime: Runtime.NODEJS_12_X,
    handler: 'point-in-time-restore-listener.handler',
    code: Code.fromAsset(path.join(lambdaDir.join('/'), 'lambda')),
    timeout: Duration.minutes(1),
    memorySize: 1024,
    environment: {
      TABLE_NAMES: tables.map((table) => table.tableName).join(','),
      APP_NAME: config.appName,
      SQS_URL: queue.queueUrl,
    },
  })

  new EventSourceMapping(stack, 'RestoreBackup-eventSource', {
    target: listener,
    eventSourceArn: queue.queueArn,
  })

  applyPermissions(backend, listener, queue)

  // Permissions for restoreTableToPointInTime(..)
  /*tables.map((table: Table) => {
    table.grant(
      backend,
      'dynamodb:RestoreTableToPointInTime',
      'dynamodb:Scan',
      'dynamodb:Query',
      'dynamodb:UpdateItem',
      'dynamodb:PutItem',
      'dynamodb:GetItem',
      'dynamodb:DeleteItem',
      'dynamodb:BatchWriteItem',
      'dynamodb:DescribeTable',
      'dynamodb:DeleteTable'
    )
  })*/

  const api = new LambdaRestApi(stack, 'RestoreBackup-API', {
    handler: backend,
    proxy: false,
    deployOptions: { stageName: config.environmentName },
  })

  /*
    TODO: RestoreSummary param from dynamoDB.describeTable(..) has a RestoreInProgress: boolean.
      I have to build a GET endpoint to check restore status
   */
  // POST <url>/backup/restore - Trigger the restore backup process
  api.root
    .addResource('backup')
    .addResource('restore')
    .addMethod('POST')

  new CfnOutput(stack, 'restoreBackupAPI-Output', {
    value: api.urlForPath('/backup/restore'),
    description: `Booster Framework backup and restore HTTP API. Methods: ${api.methods}`,
  })
}

const applyPermissions = (backend: Function, listener: Function, queue: Queue): void => {
  queue.grant(backend, 'sqs:SendMessage')
  queue.grant(listener, 'sqs:SendMessage')
  queue.grantConsumeMessages(listener)

  // Any DynamoDB table since this backend describes, restores and deletes
  // a DynamoDB table created on the fly (apart from other underground operations)
  const policyStatement = new PolicyStatement()
  policyStatement.addResources("*")
  policyStatement.addActions(
    'dynamodb:RestoreTableToPointInTime',
    'dynamodb:Scan',
    'dynamodb:Query',
    'dynamodb:UpdateItem',
    'dynamodb:PutItem',
    'dynamodb:GetItem',
    'dynamodb:DeleteItem',
    'dynamodb:BatchWriteItem',
    'dynamodb:DescribeTable',
    'dynamodb:DeleteTable',
    'dynamodb:UpdateTable'
  )

  backend.addToRolePolicy(policyStatement)
  listener.addToRolePolicy(policyStatement)
}
