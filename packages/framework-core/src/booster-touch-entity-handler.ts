import { Register, UUID } from '@boostercloud/framework-types'
import { RegisterHandler } from './booster-register-handler'
import { Booster } from './index'
import { BoosterEntityTouched } from './core-concepts/touch-entity/events/booster-entity-touched'

export class BoosterTouchEntityHandler {
  public static async touchEntity(entityName: string, entityId: UUID): Promise<void> {
    const requestID = UUID.generate()
    const register = new Register(requestID, {}, RegisterHandler.flush)
    register.events(new BoosterEntityTouched(entityName, entityId))
    return RegisterHandler.handle(Booster.config, register)
  }
}
