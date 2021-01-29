import { BoosterConfig } from '@boostercloud/framework-types'
import { App, Stack, StackProps } from '@aws-cdk/core'
import { Table } from '@aws-cdk/aws-dynamodb'
import { applyPointInTimeRecoveryBackup } from '../../src/utils/point-in-time-recovery'
import { generateDynamoDBTable } from './resource-generator'
import { expect } from '@boostercloud/framework-types/test/expect'

describe('Point in time recovery utils', () => {
  const config = new BoosterConfig('test')
  config.appName = 'testing-app'
  const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)

  it('sets the pointInTimeRecoverySpecification parameter to true', () => {
    const tables = Array.from(Array(3)).map((_, i) => {
      return generateDynamoDBTable(appStack, `myTable-${i}`, `tableName-${i}`)
    })

    tables.map((table: Table) => {
      // 'pointInTimeRecovery: false' does not set pointInTimeRecoverySpecification
      expect(table.node['host'].table.pointInTimeRecoverySpecification).to.be.undefined
    })

    applyPointInTimeRecoveryBackup(tables)

    tables.map((table: Table) => {
      expect(table.node['host'].table.pointInTimeRecoverySpecification).to.not.be.undefined
      expect(table.node['host'].table.pointInTimeRecoverySpecification.pointInTimeRecoveryEnabled).true
    })
  })
})
