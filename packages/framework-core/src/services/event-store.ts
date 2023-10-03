import {
  TraceActionTypes,
  BOOSTER_SUPER_KIND,
  BoosterConfig,
  EntityInterface,
  EntitySnapshotEnvelope,
  EventEnvelope,
  Instance,
  InvalidParameterError,
  NonPersistedEntitySnapshotEnvelope,
  ReducerGlobalError,
  SnapshotPersistHandlerGlobalError,
  UUID,
} from '@boostercloud/framework-types'
import { createInstance, getLogger } from '@boostercloud/framework-common-helpers'
import { BoosterGlobalErrorDispatcher } from '../booster-global-error-dispatcher'
import { SchemaMigrator } from '../schema-migrator'
import { BoosterEntityMigrated } from '../core-concepts/data-migration/events/booster-entity-migrated'
import { BoosterEntityTouched } from '../core-concepts/touch-entity/events/booster-entity-touched'
import { Trace } from '../instrumentation'

const originOfTime = new Date(0).toISOString() // Unix epoch

export class EventStore {
  public constructor(readonly config: BoosterConfig) {}

  /**
   * Will fetch the latest snapshot for an entity by applying a reduction
   * since the time of creation of the last snapshot or from the origin of time
   * if no snapshot is found.
   *
   * Also, in order to make next calls faster, this method caches the newly calculated
   * snapshot storing it at the end of the process.
   */
  @Trace(TraceActionTypes.FETCH_ENTITY_SNAPSHOT)
  public async fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EntitySnapshotEnvelope | undefined> {
    const logger = getLogger(this.config, 'EventStore#fetchEntitySnapshot')
    logger.debug(`Fetching snapshot for entity ${entityName} with ID ${entityID}`)
    const latestSnapshotEnvelope = await this.loadLatestSnapshot(entityName, entityID)

    const lastVisitedTime = latestSnapshotEnvelope?.snapshottedEventCreatedAt ?? originOfTime
    const pendingEvents = await this.loadEventStreamSince(entityName, entityID, lastVisitedTime)

    if (pendingEvents.length <= 0) {
      return latestSnapshotEnvelope
    } else {
      logger.debug(`Looking for the reducer for entity ${entityName} with ID ${entityID}`)
      // In this assignment we discard the `createdAt` field because it's not needed in the reduction process
      let newEntitySnapshot: NonPersistedEntitySnapshotEnvelope | undefined = latestSnapshotEnvelope
      for (const pendingEvent of pendingEvents) {
        // We double check that what we are reducing is an event
        if (pendingEvent.kind === 'event') {
          newEntitySnapshot = await this.entityReducer(pendingEvent, newEntitySnapshot)
        }
      }

      if (!newEntitySnapshot) {
        logger.debug('No snapshot was found or reduced, returning')

        return newEntitySnapshot
      }

      if (newEntitySnapshot.entityID !== entityID) {
        logger.debug(
          `Migrated entity ${entityName} with previous ID ${entityID} to ${newEntitySnapshot?.typeName} with the new ID ${newEntitySnapshot?.entityID}`,
          newEntitySnapshot
        )
      } else {
        logger.debug(`Reduced new snapshot for entity ${entityName} with ID ${entityID}: `, newEntitySnapshot)
      }

      return await this.storeSnapshot(newEntitySnapshot)
    }
  }

  @Trace(TraceActionTypes.STORE_SNAPSHOT)
  private async storeSnapshot(
    snapshot: NonPersistedEntitySnapshotEnvelope
  ): Promise<EntitySnapshotEnvelope | undefined> {
    const logger = getLogger(this.config, 'EventStore#storeSnapshot')
    try {
      logger.debug('Storing snapshot in the event store:', snapshot)
      return await this.config.provider.events.storeSnapshot(snapshot, this.config)
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
      const error = await globalErrorDispatcher.dispatch(new SnapshotPersistHandlerGlobalError(snapshot, e))
      logger.error(
        `The snapshot for entity ${snapshot.typeName} with ID ${
          snapshot.entityID
        } couldn't be stored (Tried on ${new Date()})`,
        snapshot,
        '\nError:',
        error
      )
      return
    }
  }

  @Trace(TraceActionTypes.LOAD_LATEST_SNAPSHOT)
  private async loadLatestSnapshot(entityName: string, entityID: UUID): Promise<EntitySnapshotEnvelope | undefined> {
    const logger = getLogger(this.config, 'EventStore#loadLatestSnapshot')
    logger.debug(`Loading latest snapshot for entity ${entityName} and ID ${entityID}`)
    const latestSnapshot = await this.config.provider.events.latestEntitySnapshot(this.config, entityName, entityID)
    if (latestSnapshot) {
      return new SchemaMigrator(this.config).migrate(latestSnapshot)
    }
    return undefined
  }

  @Trace(TraceActionTypes.LOAD_EVENT_STREAM_SINCE)
  private async loadEventStreamSince(
    entityTypeName: string,
    entityID: UUID,
    timestamp: string
  ): Promise<EventEnvelope[]> {
    const logger = getLogger(this.config, 'EventStore#loadEventStreamSince')
    logger.debug(`Loading list of pending events for entity ${entityTypeName} with ID ${entityID} since ${timestamp}`)
    return this.config.provider.events.forEntitySince(this.config, entityTypeName, entityID, timestamp)
  }

  @Trace(TraceActionTypes.ENTITY_REDUCER)
  private async entityReducer(
    eventEnvelope: EventEnvelope,
    latestSnapshot?: NonPersistedEntitySnapshotEnvelope
  ): Promise<NonPersistedEntitySnapshotEnvelope | undefined> {
    const logger = getLogger(this.config, 'EventStore#entityReducer')
    try {
      if (eventEnvelope.superKind && eventEnvelope.superKind === BOOSTER_SUPER_KIND) {
        if (eventEnvelope.typeName === BoosterEntityTouched.name) {
          return this.reduceEntityTouched(eventEnvelope, latestSnapshot)
        }
        if (eventEnvelope.typeName === BoosterEntityMigrated.name) {
          return this.reduceEntityMigrated(eventEnvelope)
        }
      }

      logger.debug('Calling reducer with event: ', eventEnvelope, ' and entity snapshot ', latestSnapshot)
      const eventMetadata = this.config.events[eventEnvelope.typeName]
      if (!eventMetadata) {
        logger.error(`No event registered for event ${eventEnvelope.typeName}`)
        throw new InvalidParameterError(`No event registered for event ${eventEnvelope.typeName}`)
      }
      const migratedEventEnvelope = await new SchemaMigrator(this.config).migrate(eventEnvelope)
      const eventInstance = createInstance(eventMetadata.class, migratedEventEnvelope.value)
      const entityMetadata = this.config.entities[migratedEventEnvelope.entityTypeName]
      const snapshotInstance = latestSnapshot ? createInstance(entityMetadata.class, latestSnapshot.value) : null
      try {
        const newEntity = this.reducerForEvent(migratedEventEnvelope.typeName)(eventInstance, snapshotInstance)

        const newSnapshot: NonPersistedEntitySnapshotEnvelope = {
          version: this.config.currentVersionFor(eventEnvelope.entityTypeName),
          kind: 'snapshot',
          superKind: migratedEventEnvelope.superKind,
          requestID: migratedEventEnvelope.requestID,
          entityID: migratedEventEnvelope.entityID,
          entityTypeName: migratedEventEnvelope.entityTypeName,
          typeName: migratedEventEnvelope.entityTypeName,
          value: newEntity,
          snapshottedEventCreatedAt: migratedEventEnvelope.createdAt,
        }
        logger.debug('Reducer result: ', newSnapshot)
        return newSnapshot
      } catch (e) {
        const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config)
        const error = await globalErrorDispatcher.dispatch(new ReducerGlobalError(eventInstance, snapshotInstance, e))
        throw error
      }
    } catch (e) {
      logger.error('Error when calling reducer', e)
      throw e
    }
  }

  private reduceEntityMigrated(eventEnvelope: EventEnvelope): NonPersistedEntitySnapshotEnvelope {
    const event = eventEnvelope.value as BoosterEntityMigrated
    return this.toBoosterEntitySnapshot(eventEnvelope, event.newEntity, event.newEntityName)
  }

  private reduceEntityTouched(
    eventEnvelope: EventEnvelope,
    latestSnapshot: NonPersistedEntitySnapshotEnvelope | undefined
  ): NonPersistedEntitySnapshotEnvelope | undefined {
    const logger = getLogger(this.config, 'EventStore#reduceEntityTouched')
    logger.debug('Reducing ', eventEnvelope, ' with latestSnapshot')
    if (!latestSnapshot) {
      logger.debug('Latest snapshot not found, returning')
      return
    }

    const event = eventEnvelope.value as BoosterEntityTouched
    const entityMetadata = this.config.entities[event.entityName]
    const snapshotInstance = createInstance(entityMetadata.class, latestSnapshot.value)
    return this.toBoosterEntitySnapshot(eventEnvelope, snapshotInstance, event.entityName)
  }

  private toBoosterEntitySnapshot(
    eventEnvelope: EventEnvelope,
    entity: Instance & EntityInterface,
    className: string
  ): NonPersistedEntitySnapshotEnvelope {
    const logger = getLogger(this.config, 'EventStore#toBoosterEntitySnapshot')
    const boosterMigratedSnapshot: NonPersistedEntitySnapshotEnvelope = {
      version: this.config.currentVersionFor(className),
      kind: 'snapshot',
      superKind: eventEnvelope.superKind,
      requestID: eventEnvelope.requestID,
      entityID: entity.id,
      entityTypeName: className,
      typeName: className,
      value: entity,
      snapshottedEventCreatedAt: eventEnvelope.createdAt,
    }
    logger.debug('BoosterEntitySnapshot result: ', boosterMigratedSnapshot)
    return boosterMigratedSnapshot
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
