import { Register, UUID } from '@boostercloud/framework-types'
import { Entity, Event, EventHandler, Reduces } from '../decorators'
import { Booster } from '../booster'
import { ReadModelStore } from '../services/read-model-store'
import { EventStore } from '../services/event-store'
import { buildLogger } from '../booster-logger'

@Event
export class BoosterAppDeployed {
  public constructor(readonly id: UUID) {}

  public entityID(): UUID {
    return this.id
  }
}

@EventHandler(BoosterAppDeployed)
export class BoosterHandleReadModelFillOperation {
  public static async handle(event: BoosterAppDeployed, register: Register): Promise<void> {
    const config = Booster.config
    const logger = buildLogger(config.logLevel)
    const eventStore = new EventStore(config, logger)
    const readModelStore = new ReadModelStore(config, logger)

    for (const entityName in config.entities) {
      if (entityName === BoosterPostDeploy.name) continue
      const uniqueEntityIDs = await config.provider.events.getUniqueEntityIDs(config, logger, entityName)

      if (uniqueEntityIDs.length > 0) {
        for (let j = 0; j < uniqueEntityIDs.length; j++) {
          const id = uniqueEntityIDs[j]
          const snapshot = await eventStore.fetchEntitySnapshot(entityName, id)
          if (snapshot) {
            logger.debug(`[BoosterAppDeployed-eventHandler] - Projecting ${snapshot}`)
            await readModelStore.project(snapshot)
          } else {
            logger.debug('[BoosterAppDeployed-eventHandler] - Skipping projection, snapshot was not found')
          }
        }
      }
    }
  }
}

@Entity
export class BoosterPostDeploy {
  public constructor(readonly id: UUID) {}

  @Reduces(BoosterAppDeployed)
  public static boosterAppWasDeployed(event: BoosterAppDeployed): BoosterPostDeploy {
    return new BoosterPostDeploy(event.id)
  }
}
