import {
  BOOSTER_SUPER_KIND,
  BoosterConfig,
  DOMAIN_SUPER_KIND,
  EventEnvelope,
  EventInterface,
  Instance,
  NotFoundError,
  Register,
  SuperKindType,
  ReducerMetadata,
} from '@boostercloud/framework-types'
import { BoosterEntityMigrated } from './core-concepts/data-migration/events/booster-entity-migrated'
import { BoosterDataMigrationStarted } from './core-concepts/data-migration/events/booster-data-migration-started'
import { BoosterDataMigrationFinished } from './core-concepts/data-migration/events/booster-data-migration-finished'

const boosterEventsTypesNames: Array<string> = [
  BoosterEntityMigrated.name,
  BoosterDataMigrationStarted.name,
  BoosterDataMigrationFinished.name,
]

export class RegisterHandler {
  public static async handle(config: BoosterConfig, register: Register): Promise<void> {
    if (register.eventList.length == 0) {
      return
    }
    return config.provider.events.store(
      register.eventList.map(RegisterHandler.wrapEvent.bind(null, register, config)),
      config
    )
  }

  private static wrapEvent(register: Register, config: BoosterConfig, event: Instance & EventInterface): EventEnvelope {
    const eventTypeName = event.constructor.name
    const entityTypeName = RegisterHandler.getEntityTypeName(eventTypeName, event, config)
    if (!entityTypeName) {
      throw new NotFoundError(
        `Couldn't find information about event ${eventTypeName}. Is the event handled by an entity?`
      )
    }
    if (!event.entityID || !event.entityID()) {
      throw new Error(
        `Event ${eventTypeName} has an empty 'entityID' or the required 'entityID' method was not implemented. Make sure to return a string-compatible value identifying the entity this event belongs to.`
      )
    }

    return {
      version: config.currentVersionFor(eventTypeName),
      kind: 'event',
      superKind: RegisterHandler.getSuperKind(eventTypeName),
      entityID: event.entityID(),
      requestID: register.requestID,
      currentUser: register.currentUser,
      entityTypeName: entityTypeName,
      typeName: eventTypeName,
      value: event,
      createdAt: new Date().toISOString(), // TODO: This could be overridden by the provider. We should not set it. Ensure all providers set it
    }
  }

  private static getSuperKind(eventTypeName: string): SuperKindType {
    return boosterEventsTypesNames.includes(eventTypeName) ? BOOSTER_SUPER_KIND : DOMAIN_SUPER_KIND
  }

  private static getEntityTypeName(
    eventTypeName: string,
    event: Instance & EventInterface,
    config: BoosterConfig
  ): string {
    if (eventTypeName === BoosterEntityMigrated.name) {
      return (event as BoosterEntityMigrated).oldEntityName
    }
    const reducerInfo: ReducerMetadata | undefined = config.reducers[eventTypeName]
    return reducerInfo?.class?.name
  }
}
