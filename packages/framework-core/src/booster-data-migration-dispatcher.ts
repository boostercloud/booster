import { BoosterConfig, EntityInterface, Register, UUID } from '@boostercloud/framework-types'
import { BoosterEntityMigrated } from './core-concepts/data-migration/events/booster-entity-migrated'
import { RegisterHandler } from './booster-register-handler'

export class BoosterDataMigrationDispatcher {
  public constructor(readonly config: BoosterConfig) {}

  public async dispatch(entityName: string, oldEntityId: UUID, newEntity: EntityInterface): Promise<void> {
    const requestID = UUID.generate()
    const register = new Register(requestID)
    register.events(new BoosterEntityMigrated(entityName, oldEntityId, newEntity))
    await RegisterHandler.handle(this.config, register)
  }
}
