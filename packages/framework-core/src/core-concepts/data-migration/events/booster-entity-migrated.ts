import { EntityInterface, Instance, UUID } from '@boostercloud/framework-types'

export class BoosterEntityMigrated {
  public constructor(
    readonly entityName: string,
    readonly oldEntityId: UUID,
    readonly newEntity: Instance & EntityInterface
  ) {}

  public entityID(): UUID {
    return this.oldEntityId
  }
}
