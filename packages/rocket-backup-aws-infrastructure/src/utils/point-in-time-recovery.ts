import { Stack, Duration, CfnOutput } from '@aws-cdk/core'
import { Function, Runtime, Code } from '@aws-cdk/lambda'
import { LambdaRestApi } from '@aws-cdk/aws-apigateway'
import { Table } from '@aws-cdk/aws-dynamodb'
import { BackupStackParams } from './types'
import * as path from 'path'

export const applyPointInTimeRecoveryBackup = (stack: Stack, params: BackupStackParams, tables: Array<Table>): void => {
  tables.map((table: Table) => {
    table.node['host'].table.pointInTimeRecoverySpecification = {
      pointInTimeRecoveryEnabled: true,
    }
  })

  if (params.pointInTimeRules?.generateRestoreAPI) {
    createRestoreAPI(stack, tables)
  }
}

export const createRestoreAPI = (stack: Stack, tables: Array<Table>): void => {
  console.log('///// WORKING DIRECTORY /////')
  console.log(__dirname)
  console.log(process.cwd())
  const lambdaDir = __dirname.split('/')
  lambdaDir.pop()
  console.log(lambdaDir)

  const backend = Function(stack, 'RestoreBackup-Backend', {
    runtime: Runtime.NODEJS_12_X,
    handler: 'point-in-time-restore-backend.handler',
    code: Code.fromAsset(path.join(__dirname, 'lambda')),
    timeout: Duration.minutes(1),
    memorySize: 1024,
    environment: {
      TABLE_NAMES: tables.map((table) => table.tableName).join(','),
    },
  })

  // Permissions:
  // 1. restoreTableToPointInTime(..) -
  tables.map((table) => {
    table.grant(backend, '', '')
  })

  const api = new LambdaRestApi(stack, 'RestoreBackup-API', {
    handler: backend,
  })

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
