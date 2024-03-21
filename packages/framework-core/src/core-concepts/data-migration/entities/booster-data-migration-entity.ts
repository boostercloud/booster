import { BoosterDataMigrationEntityDuration, DataMigrationStatus } from '@boostercloud/framework-types'
import { BoosterDataMigrationStarted } from '../events/booster-data-migration-started'
import { BoosterDataMigrationFinished } from '../events/booster-data-migration-finished'

export class BoosterDataMigrationEntity {
  public constructor(
    public id: string,
    public status: DataMigrationStatus,
    public lastUpdated: string,
    public duration?: BoosterDataMigrationEntityDuration
  ) {}

  public static started(
    event: BoosterDataMigrationStarted,
    currentDataMigration: BoosterDataMigrationEntity
  ): BoosterDataMigrationEntity {
    const duration = {
      start: new Date().toISOString(),
    }
    return new BoosterDataMigrationEntity(event.name, DataMigrationStatus.RUNNING, event.lastUpdated, duration)
  }

  public static finished(
    event: BoosterDataMigrationFinished,
    currentDataMigration: BoosterDataMigrationEntity
  ): BoosterDataMigrationEntity {
    const current = new Date()
    if (currentDataMigration.duration?.start) {
      const start = currentDataMigration.duration.start
      const end = current.toISOString()
      const startTime = Date.parse(start)
      const endTime = current.getTime()
      const elapsedTime = endTime - startTime
      const duration: BoosterDataMigrationEntityDuration = {
        start: start,
        end: end,
        elapsedMilliseconds: elapsedTime,
      }
      return new BoosterDataMigrationEntity(event.name, DataMigrationStatus.FINISHED, event.lastUpdated, duration)
    }
    return new BoosterDataMigrationEntity(event.name, DataMigrationStatus.FINISHED, event.lastUpdated)
  }
}
