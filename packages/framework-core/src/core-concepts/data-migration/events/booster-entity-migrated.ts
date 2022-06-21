import { EntityInterface, Instance, UUID } from '@boostercloud/framework-types'

export class BoosterEntityMigrated {
  public constructor(
    readonly oldEntityName: string,
    readonly oldEntityId: UUID,
    readonly newEntityName: string,
    readonly newEntity: Instance & EntityInterface
  ) {}

  public entityID(): UUID {
    return this.oldEntityId
  }
}
