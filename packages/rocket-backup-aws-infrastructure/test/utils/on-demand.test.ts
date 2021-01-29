import { BoosterConfig } from '@boostercloud/framework-types'
import { App, Stack, StackProps } from '@aws-cdk/core'
import { BackupPlan, BackupResource } from '@aws-cdk/aws-backup'
import { Table } from '@aws-cdk/aws-dynamodb'
import * as OnDemandUtils from '../../src/utils/on-demand'
import { generateDynamoDBTable } from './resource-generator'
// eslint-disable-next-line import/no-extraneous-dependencies
import { spy, restore } from 'sinon'
import { expect } from '@boostercloud/framework-types/test/expect'

describe('On demand utils', () => {
  const config = new BoosterConfig('test')
  config.appName = 'testing-app'
  let appStack: Stack
  let tables: Array<Table>
  let backupResources: Array<BackupResource>
  const backUpPlanID = 'BackupPlan'
  const backupSelectionID = 'BackupSelection'

  beforeEach(() => {
    restore()
    appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)
    tables = Array.from(Array(3)).map((_, i) => {
      return generateDynamoDBTable(appStack, `myTable-${i}`, `tableName-${i}`)
    })
    backupResources = tables.map((table: Table) => {
      return BackupResource.fromDynamoDbTable(table)
    }) as Array<BackupResource>
  })

  it('creates a backup plan when no rules have been provided through the onDemandBackupRules parameter', () => {
    const params = { backupType: 'ON_DEMAND' }
    const backupPlanSpy = spy(BackupPlan, 'dailyMonthly1YearRetention')
    const backupPlanAddSelectionSpy = spy(BackupPlan.prototype, 'addSelection')
    // Spying on this method separately because dailyMonthly1YearRetention already triggers "BackupPlan.addRule(...)" twice
    const backupPlanAddRuleSpy = spy(OnDemandUtils, 'addAdditionalRules')

    OnDemandUtils.applyOnDemandBackup(appStack, params, tables)

    expect(backupPlanSpy).to.have.been.calledOnceWithExactly(appStack, backUpPlanID)
    expect(backupPlanAddSelectionSpy).to.have.been.calledOnceWithExactly(backupSelectionID, {
      resources: backupResources,
    })
    expect(backupPlanAddRuleSpy).to.not.have.been.called
  })

  it('creates a backup plan when all rules have been provided through the onDemandBackupRules parameter', () => {
    // Weekday is missing since it can't be set with the 'day' parameter
    const onDemandBackupRules = {
      minute: '40',
      hour: '18',
      day: '10',
      month: '6',
      year: '2021',
    }
    const params = {
      backupType: 'ON_DEMAND',
      onDemandBackupRules,
    }
    const backupPlanSpy = spy(BackupPlan, 'dailyMonthly1YearRetention')
    const backupPlanAddSelectionSpy = spy(BackupPlan.prototype, 'addSelection')
    // Spying on this method separately because dailyMonthly1YearRetention already triggers "BackupPlan.addRule(...)" twice
    const backupPlanAddRuleSpy = spy(OnDemandUtils, 'addAdditionalRules')

    OnDemandUtils.applyOnDemandBackup(appStack, params, tables)

    expect(backupPlanSpy).to.have.been.calledOnceWithExactly(appStack, backUpPlanID)
    expect(backupPlanAddSelectionSpy).to.have.been.calledOnceWithExactly(backupSelectionID, {
      resources: backupResources,
    })
    expect(backupPlanAddRuleSpy).to.have.been.calledOnceWithExactly(backupPlanSpy.returnValues[0], onDemandBackupRules)
  })
})
