import {
  BoosterConfig,
  UUID,
  EventEnvelope,
  InvalidParameterError,
  ReducerGlobalError,
} from '@boostercloud/framework-types'
import { createInstance, getLogger } from '@boostercloud/framework-common-helpers'
import { BoosterGlobalErrorDispatcher } from '../booster-global-error-dispatcher'
import { Migrator } from '../migrator'

const originOfTime = new Date(0).toISOString() // Unix epoch

export class EventStore {
  public constructor(readonly config: BoosterConfig) {}

  public async fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EventEnvelope | null> {
    const logger = getLogger(this.config, 'EventStore#fetchEntitySnapshot')
    logger.debug(`Fetching snapshot for entity ${entityName} with ID ${entityID}`)
    const latestSnapshotEnvelope = await this.loadLatestSnapshot(entityName, entityID)

    // eslint-disable-next-line @typescript-eslint/no-extra-parens
    const lastVisitedTime = latestSnapshotEnvelope?.snapshottedEventCreatedAt ?? originOfTime
    const pendingEvents = await this.loadEventStreamSince(entityName, entityID, lastVisitedTime)

    if (pendingEvents.length <= 0) {
      return latestSnapshotEnvelope
    } else {
      logger.debug(
        `[EventStore#fetchEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
      )
      let newEntitySnapshot = latestSnapshotEnvelope
      for (const pendingEvent of pendingEvents) {
        newEntitySnapshot = await this.entityReducer(newEntitySnapshot, pendingEvent)
      }

      logger.debug(
        `[EventStore#fetchEntitySnapshot] Reduced new snapshot for entity ${entityName} with ID ${entityID}: `,
        newEntitySnapshot
      )

      return newEntitySnapshot
    }
  }

  public async calculateAndStoreEntitySnapshot(
    entityName: string,
    entityID: UUID,
    pendingEnvelopes: Array<EventEnvelope>
  ): Promise<EventEnvelope | null> {
    const logger = getLogger(this.config, 'EventStore#calculateAndStoreEntitySnapshot')
    logger.debug('Processing events: ', pendingEnvelopes)
    logger.debug(`Fetching snapshot for entity ${entityName} with ID ${entityID}`)
    const latestSnapshotEnvelope = await this.loadLatestSnapshot(entityName, entityID)

    logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
    )
    let newEntitySnapshot = latestSnapshotEnvelope
    for (const pendingEvent of pendingEnvelopes) {
      newEntitySnapshot = await this.entityReducer(newEntitySnapshot, pendingEvent)
    }

    logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Reduced new snapshot for entity ${entityName} with ID ${entityID}: `,
      newEntitySnapshot
    )

    if (!newEntitySnapshot) {
      logger.debug('New entity snapshot is null. Returning old one (which can also be null)')
      return latestSnapshotEnvelope
    }

    await this.storeSnapshot(newEntitySnapshot)

    return newEntitySnapshot
  }

  private async storeSnapshot(snapshot: EventEnvelope): Promise<void> {
    const logger = getLogger(this.config, 'EventStore#storeSnapshot')
    logger.debug('Storing snapshot in the event store:', snapshot)
    return this.config.provider.events.store([snapshot], this.config)
  }

  private loadLatestSnapshot(entityName: string, entityID: UUID): Promise<EventEnvelope | null> {
    const logger = getLogger(this.config, 'EventStore#loadLatestSnapshot')
    logger.debug(`Loading latest snapshot for entity ${entityName} and ID ${entityID}`)
    return this.config.provider.events.latestEntitySnapshot(this.config, entityName, entityID)
  }

  private loadEventStreamSince(entityTypeName: string, entityID: UUID, timestamp: string): Promise<EventEnvelope[]> {
    const logger = getLogger(this.config, 'EventStore#loadEventStreamSince')
    logger.debug(`Loading list of pending events for entity ${entityTypeName} with ID ${entityID} since ${timestamp}`)
    return this.config.provider.events.forEntitySince(this.config, entityTypeName, entityID, timestamp)
  }

  private async entityReducer(
    latestSnapshot: EventEnvelope | null,
    eventEnvelope: EventEnvelope
  ): Promise<EventEnvelope> {
    const logger = getLogger(this.config, 'EventStore#entityReducer')
    try {
      logger.debug('Calling reducer with event: ', eventEnvelope, ' and entity snapshot ', latestSnapshot)
      const eventMetadata = this.config.events[eventEnvelope.typeName]
      const migratedEventEnvelope = await new Migrator(this.config).migrate(eventEnvelope)
      const eventInstance = createInstance(eventMetadata.class, migratedEventEnvelope.value)
      const entityMetadata = this.config.entities[migratedEventEnvelope.entityTypeName]
      let migratedLatestSnapshot: EventEnvelope | null = null
      if (latestSnapshot) {
        migratedLatestSnapshot = await new Migrator(this.config).migrate(latestSnapshot)
      }
      const snapshotInstance = migratedLatestSnapshot
        ? createInstance(entityMetadata.class, migratedLatestSnapshot.value)
        : null
      let newEntity: any
      try {
        newEntity = this.reducerForEvent(migratedEventEnvelope.typeName)(eventInstance, snapshotInstance)
      } catch (e) {
        const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
        const error = await globalErrorDispatcher.dispatch(new ReducerGlobalError(eventInstance, snapshotInstance, e))
        if (error) throw error
      }

      const newSnapshot: EventEnvelope = {
        version: this.config.currentVersionFor(eventEnvelope.entityTypeName),
        kind: 'snapshot',
        requestID: migratedEventEnvelope.requestID,
        entityID: migratedEventEnvelope.entityID,
        entityTypeName: migratedEventEnvelope.entityTypeName,
        typeName: migratedEventEnvelope.entityTypeName,
        value: newEntity,
        createdAt: new Date().toISOString(), // TODO: This could be overridden by the provider. We should not set it. Ensure all providers set it
        snapshottedEventCreatedAt: migratedEventEnvelope.createdAt,
      }
      logger.debug('Reducer result: ', newSnapshot)
      return newSnapshot
    } catch (e) {
      logger.error('Error when calling reducer', e)
      throw e
    }
  }

  // eslint-disable-next-line @typescript-eslint/ban-types
  private reducerForEvent(eventName: string): Function {
    const logger = getLogger(this.config, 'EventStore#reducerForEvent')
    const reducerMetadata = this.config.reducers[eventName]
    if (!reducerMetadata) {
      throw new InvalidParameterError(`No reducer registered for event ${eventName}`)
    } else {
      try {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const reducer = (reducerMetadata.class as any)[reducerMetadata.methodName]
        logger.debug(
          `Found reducer for event ${eventName}: "${reducerMetadata.class.name}.${reducerMetadata.methodName}"`
        )
        return reducer
      } catch {
        throw new Error(`Couldn't load the Entity class ${reducerMetadata.class.name}`)
      }
    }
  }
}
