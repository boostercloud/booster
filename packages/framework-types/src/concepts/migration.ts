import { AnyClass } from '../typelevel'

export interface MigrationMetadata {
  migrationClass: AnyClass
  methodName: string
  toVersion: number
  fromSchema: AnyClass
  toSchema: AnyClass
}
