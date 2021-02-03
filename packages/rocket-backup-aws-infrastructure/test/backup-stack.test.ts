import { BoosterConfig } from '@boostercloud/framework-types'
import { App, Stack, StackProps } from '@aws-cdk/core'
// eslint-disable-next-line import/no-extraneous-dependencies
import { restore, stub } from 'sinon'
import { BackupStack } from '../src/backup-stack'
import { Table } from '@aws-cdk/aws-dynamodb'
import * as PointInTimeRecoveryUtils from '../src/utils/point-in-time-recovery'
import * as OnDemandUtils from '../src/utils/on-demand'
import { generateDynamoDBTable } from './utils/resource-generator'
import { expect } from '@boostercloud/framework-types/test/expect'

describe('Backup stack', () => {
  const config = new BoosterConfig('test')
  config.appName = 'testing-app'
  const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)
  let tables: Array<Table>

  before(() => {
    tables = Array.from(Array(3)).map((_, i) => {
      return generateDynamoDBTable(appStack, `myTable-${i}`, `tableName-${i}`)
    })
  })

  afterEach(() => {
    restore()
  })

  context('when BackupType is ON_DEMAND', () => {
    it('calls applyOnDemandBackup method', () => {
      const params = { backupType: 'ON_DEMAND' }
      const onDemandBackupStub = stub(OnDemandUtils, 'applyOnDemandBackup').returns(undefined)
      BackupStack.mountStack(params, appStack)

      expect(onDemandBackupStub).to.have.been.calledOnceWithExactly(appStack, params, tables)
    })
  })

  context('when BackupType is POINT_IN_TIME', () => {
    it('calls applyPointInTimeRecoveryBackup', () => {
      const params = { backupType: 'POINT_IN_TIME' }
      const pointIntimeBackupStub = stub(PointInTimeRecoveryUtils, 'applyPointInTimeRecoveryBackup').returns(undefined)

      BackupStack.mountStack(params, appStack)

      expect(pointIntimeBackupStub).to.have.been.calledOnceWithExactly(tables)
    })
  })
})
