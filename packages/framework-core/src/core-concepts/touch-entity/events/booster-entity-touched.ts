import { UUID } from '@boostercloud/framework-types'

export class BoosterEntityTouched {
  public constructor(readonly entityName: string, readonly entityId: UUID) {}

  public entityID(): UUID {
    return this.entityId
  }
}
