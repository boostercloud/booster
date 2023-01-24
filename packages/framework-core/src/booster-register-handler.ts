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
  UUID,
} from '@boostercloud/framework-types'
import { BoosterEntityMigrated } from './core-concepts/data-migration/events/booster-entity-migrated'
import { BoosterDataMigrationStarted } from './core-concepts/data-migration/events/booster-data-migration-started'
import { BoosterDataMigrationFinished } from './core-concepts/data-migration/events/booster-data-migration-finished'
import { Booster } from './booster'

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
      register.eventList.map((event) => RegisterHandler.wrapEvent(config, event, register)),
      config
    )
  }

  public static async flush(record: Register): Promise<void> {
    return RegisterHandler.handle(Booster.config, record)
  }

  private static wrapEvent(config: BoosterConfig, event: Instance & EventInterface, register: Register): EventEnvelope {
    const eventTypeName = event.constructor.name
    const entityTypeName = RegisterHandler.getEntityTypeName(eventTypeName, event, config)
    if (!entityTypeName) {
      throw new NotFoundError(
        `Couldn't find information about event ${eventTypeName}. Is the event handled by an entity?`
      )
    }
    const entityID = RegisterHandler.getEntityID(event, config)
    if (!event.entityID || !event.entityID()) {
      throw new Error(
        `Event ${eventTypeName} has an empty 'entityID' or the required 'entityID' method was not implemented. Make sure to return a string-compatible value identifying the entity this event belongs to.`
      )
    }

    return {
      version: config.currentVersionFor(eventTypeName),
      kind: 'event',
      superKind: RegisterHandler.getSuperKind(eventTypeName),
      entityID,
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
    const reducerInfo = config.reducers[eventTypeName]
    return reducerInfo.class.name
  }

  private static getEntityID(event: Instance & EventInterface, config: BoosterConfig): UUID {
    const eventName = event.constructor.name
    const evtObject = event as Record<string, unknown>
    const entityIdField = config.entityIdFields[eventName]
    if (entityIdField && entityIdField in evtObject && typeof evtObject[entityIdField] === 'string') {
      return evtObject[entityIdField] as UUID
    }
    if (eventName in config.notifications) {
      return RegisterHandler.getDefaultNotificationTopic(event)
    }
    return RegisterHandler.getDefaultStateEventEntityId(event)
  }

  private static getDefaultStateEventEntityId(event: Instance & EventInterface): UUID {
    if (event.entityID && typeof event.entityID === 'function') {
      return event.entityID()
    }
    if (event.id && typeof event.id === 'string') {
      return event.id
    }
    throw new Error(
      `Event ${event.constructor.name} has no specification for the entity ID. Make sure to specify a string-compatible value identifying the entity this event belongs to.

      You can do it by:

      1. Using the @EntityID decorator to mark a field as the entity ID
      2. Adding an id field to the event
      3. Adding an entityID method to the event`
    )
  }

  private static getDefaultNotificationTopic(event: Instance & EventInterface): UUID {
    if (event.topic && typeof event.topic === 'string') {
      return event.topic
    }

    throw new Error(
      `Event ${event.constructor.name} has no specification for the topic. Make sure to specify a string-compatible value identifying the topic this event belongs to.

      You can do it by:

      1. Using the @Topic decorator to mark a field as the topic
      2. Adding a topic field to the event`
    )
  }
}
