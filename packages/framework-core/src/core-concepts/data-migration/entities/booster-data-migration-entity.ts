import { DataMigrationStatus } from '@boostercloud/framework-types'
import { BoosterDataMigrationStarted } from '../events/booster-data-migration-started'
import { BoosterDataMigrationFinished } from '../events/booster-data-migration-finished'

export class BoosterDataMigrationEntity {
  public constructor(public id: string, public status: DataMigrationStatus, public lastUpdated: string) {}

  public static started(
    event: BoosterDataMigrationStarted,
    currentDataMigration: BoosterDataMigrationEntity
  ): BoosterDataMigrationEntity {
    return new BoosterDataMigrationEntity(event.name, DataMigrationStatus.RUNNING, event.lastUpdated)
  }

  public static finished(
    event: BoosterDataMigrationFinished,
    currentDataMigration: BoosterDataMigrationEntity
  ): BoosterDataMigrationEntity {
    return new BoosterDataMigrationEntity(event.name, DataMigrationStatus.FINISHED, event.lastUpdated)
  }
}
