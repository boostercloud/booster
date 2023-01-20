import { AnyClass } from '../typelevel'

export interface SchemaMigrationMetadata {
  migrationClass: AnyClass
  methodName: string
  toVersion: number
  fromSchema: AnyClass
  toSchema: AnyClass
}
