import { Table } from '@aws-cdk/aws-dynamodb'

export const applyPointInTimeRecoveryBackup = (tables: Array<Table>): void => {
  tables.map((table: Table) => {
    table.node['host'].table.pointInTimeRecoverySpecification = {
      pointInTimeRecoveryEnabled: true,
    }
  })
}
