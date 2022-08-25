import { UUID } from '@boostercloud/framework-types'

export class BoosterDataMigrationStarted {
  readonly lastUpdated: string

  public constructor(readonly name: string, lastUpdated?: string) {
    this.lastUpdated = lastUpdated ?? new Date().toISOString()
  }

  public entityID(): UUID {
    return this.name
  }
}
