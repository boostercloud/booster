import { AnyClass } from '../typelevel'
import { Register } from './register'
import { DataMigrationParameters } from '../data-migration-parameters'

export enum DataMigrationStatus {
  'RUNNING',
  'FINISHED',
}

export interface DataMigrationInterface extends AnyClass {
  start(register: Register): Promise<void>
}

export interface DataMigrationMetadata {
  readonly class: DataMigrationInterface
  migrationOptions: DataMigrationParameters
}

export interface BoosterDataMigrationEntityDuration {
  start: string
  end?: string
  elapsedMilliseconds?: number
}
