import { Stack, Duration, CfnOutput } from '@aws-cdk/core'
import { Function, Runtime, Code } from '@aws-cdk/aws-lambda'
import { LambdaRestApi } from '@aws-cdk/aws-apigateway'
import { Table } from '@aws-cdk/aws-dynamodb'
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
  const boosterEnv = config.environmentName
  lambdaDir.pop()

  const backend = new Function(stack, 'RestoreBackup-Backend', {
    runtime: Runtime.NODEJS_12_X,
    handler: 'point-in-time-restore-backend.handler',
    code: Code.fromAsset(path.join(lambdaDir.join('/'), 'lambda')),
    timeout: Duration.minutes(1),
    memorySize: 1024,
    environment: {
      TABLE_NAMES: tables.map((table) => table.tableName).join(','),
      APP_NAME: config.appName,
      BOOSTER_ENV: boosterEnv,
    },
  })

  // Permissions for restoreTableToPointInTime(..)
  tables.map((table: Table) => {
    table.grant(
      backend,
      'dynamodb:RestoreTableToPointInTime',
      'dynamodb:Scan',
      'dynamodb:Query',
      'dynamodb:UpdateItem',
      'dynamodb:PutItem',
      'dynamodb:GetItem',
      'dynamodb:DeleteItem',
      'dynamodb:BatchWriteItem'
    )
  })

  const api = new LambdaRestApi(stack, 'RestoreBackup-API', {
    handler: backend,
    proxy: false,
    deployOptions: { stageName: boosterEnv },
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
