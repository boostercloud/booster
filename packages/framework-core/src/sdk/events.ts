import {
  BOOSTER_SUPER_KIND,
  DOMAIN_SUPER_KIND,
  EventInterface,
  Instance,
  SuperKindType,
  UUID,
  UserEnvelope,
  EventEnvelope,
  NotFoundError,
  BoosterConfig,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { EventStore } from '../services/event-store'
import { BoosterEntityMigrated } from '../core-concepts/data-migration/events/booster-entity-migrated'
import { BoosterDataMigrationStarted } from '../core-concepts/data-migration/events/booster-data-migration-started'
import { BoosterDataMigrationFinished } from '../core-concepts/data-migration/events/booster-data-migration-finished'

interface EventsContext {
  requestID: UUID
  currentUser?: UserEnvelope
}

const boosterEventsTypesNames: Array<string> = [
  BoosterEntityMigrated.name,
  BoosterDataMigrationStarted.name,
  BoosterDataMigrationFinished.name,
]

/**
 * Public Events class that can be used from command or event handlers to perform operations on events
 */
export class Events {
  private constructor(private config: BoosterConfig) {}

  public static with(config: BoosterConfig): Events {
    return new Events(config)
  }

  /**
   * Stores an array of events in the event store in the order they are provided
   * @param events An array of events to be stored
   * @param context A context object containing the requestID and the currentUser if it exists
   */
  public async store(events: Array<EventInterface>, context: EventsContext): Promise<void> {
    if (events.length == 0) {
      return
    }
    getLogger(this.config, 'Events#store').debug('Storing events:', events)
    const eventStore = new EventStore(this.config)
    await eventStore.storeEvents(events.map(this.wrapEvent.bind(null, context)))
  }

  private wrapEvent(context: EventsContext, event: Instance & EventInterface): EventEnvelope {
    const eventTypeName = event.constructor.name
    const entityTypeName = this.getEntityTypeName(eventTypeName, event)
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
      version: this.config.currentVersionFor(eventTypeName),
      kind: 'event',
      superKind: this.getSuperKind(eventTypeName),
      entityID: event.entityID(),
      requestID: context.requestID,
      currentUser: context.currentUser,
      entityTypeName: entityTypeName,
      typeName: eventTypeName,
      value: event,
      createdAt: new Date().toISOString(), // TODO: This could be overridden by the provider. We should not set it. Ensure all providers set it
    }
  }

  private getSuperKind(eventTypeName: string): SuperKindType {
    return boosterEventsTypesNames.includes(eventTypeName) ? BOOSTER_SUPER_KIND : DOMAIN_SUPER_KIND
  }

  private getEntityTypeName(eventTypeName: string, event: Instance & EventInterface): string {
    if (eventTypeName === BoosterEntityMigrated.name) {
      return (event as BoosterEntityMigrated).oldEntityName
    }
    const reducerInfo = this.config.reducers[eventTypeName]
    return reducerInfo.class.name
  }
}
