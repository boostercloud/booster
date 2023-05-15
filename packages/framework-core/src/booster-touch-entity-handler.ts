import {
  EntityTouchStatus,
  Register,
  TouchEntityInterface,
  TouchEntityMetadata,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { RegisterHandler } from './booster-register-handler'
import { EventStore } from './services/event-store'
import { Booster } from './index'
import { BoosterEntityTouchStarted } from './core-concepts/touch-entity/events/booster-entity-touch-started'
import { BoosterTouchEntityEntity } from './core-concepts/touch-entity/entities/booster-touch-entity-entity'
import { BoosterEntityTouched } from './core-concepts/touch-entity/events/booster-entity-touched'

export class BoosterTouchEntityHandler {
  public static async run(): Promise<boolean> {
    const config = Booster.config
    const logger = getLogger(config, 'BoosterTouchEntity#run')
    let touching = false

    const configuredTouchEntities = config.touchEntityHandlers
    if (Object.keys(configuredTouchEntities).length === 0) {
      logger.debug('No defined touch entities found. Skipping...')
      return false
    }

    const sortedConfiguredTouch = BoosterTouchEntityHandler.sortConfiguredTouchEntities(configuredTouchEntities)
    const eventStore = new EventStore(config)
    for (const configuredTouchEntity of Object.values(sortedConfiguredTouch)) {
      const touchEntityForConfiguredTouch = await eventStore.fetchEntitySnapshot(
        BoosterTouchEntityEntity.name,
        configuredTouchEntity.class.name
      )
      if (!touchEntityForConfiguredTouch) {
        logger.debug(
          'Not found running or finished touch newEntity for the configured touch newEntity',
          configuredTouchEntity
        )
        touching = true
        await BoosterTouchEntityHandler.touch(configuredTouchEntity)
      } else {
        const boosterEntityTouchEntity = touchEntityForConfiguredTouch.value as BoosterTouchEntityEntity
        if (boosterEntityTouchEntity.status === EntityTouchStatus.RUNNING) {
          logger.debug('Found running touch newEntity for the configured touch newEntity', configuredTouchEntity)
          touching = true
        }
      }
    }

    return touching
  }

  public static async touchEntity(entityName: string, entityId: UUID): Promise<void> {
    const requestID = UUID.generate()
    const register = new Register(requestID, {}, RegisterHandler.flush)
    register.events(new BoosterEntityTouched(entityName, entityId))
    return RegisterHandler.handle(Booster.config, register)
  }

  private static sortConfiguredTouchEntities(
    configuredTouchEntities: Record<string, TouchEntityMetadata>
  ): Array<TouchEntityMetadata> {
    return Object.values(configuredTouchEntities).sort((a: TouchEntityMetadata, b: TouchEntityMetadata) => {
      return a.touchOptions.order - b.touchOptions.order
    })
  }

  private static async touch(touchHandler: TouchEntityMetadata): Promise<void> {
    const startedRegister = new Register(UUID.generate(), {}, RegisterHandler.flush)

    await BoosterTouchEntityHandler.emitStarted(startedRegister, touchHandler.class.name)
    await RegisterHandler.handle(Booster.config, startedRegister)

    const finishedRegister = new Register(UUID.generate(), {}, RegisterHandler.flush)
    await (touchHandler.class as TouchEntityInterface).start(finishedRegister)
    await RegisterHandler.handle(Booster.config, finishedRegister)
  }

  private static async emitStarted(register: Register, configuredTouchEntityName: string): Promise<void> {
    const logger = getLogger(Booster.config, 'BoosterTouchEntity#emitStarted')
    logger.info('Touch started', configuredTouchEntityName)
    register.events(new BoosterEntityTouchStarted(configuredTouchEntityName, new Date().toISOString()))
  }
}
