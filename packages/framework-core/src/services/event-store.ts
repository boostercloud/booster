import {
  BoosterConfig,
  ProviderLibrary,
  Logger,
  UUID,
  EventEnvelope,
  InvalidParameterError,
  ReducerGlobalError,
} from '@boostercloud/framework-types'
import { createInstance } from '@boostercloud/framework-common-helpers'
import { BoosterGlobalErrorDispatcher } from '../booster-global-error-dispatcher'
import { Migrator } from '../migrator'

const originOfTime = new Date(0).toISOString() // Unix epoch

export class EventStore {
  private config: BoosterConfig
  private provider: ProviderLibrary
  private logger: Logger
  private migrator: Migrator

  public constructor(config: BoosterConfig, logger: Logger) {
    this.config = config
    this.provider = config.provider
    this.logger = logger
    this.migrator = new Migrator(this.config, this.logger)
  }

  public async fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EventEnvelope | null> {
    this.logger.debug(`[EventStore#fetchEntitySnapshot] Fetching snapshot for entity ${entityName} with ID ${entityID}`)
    const latestSnapshotEnvelope = await this.loadLatestSnapshot(entityName, entityID)
    const migrateSnapshotEnvelope = await this.migrateSnapshot(latestSnapshotEnvelope)

    // eslint-disable-next-line @typescript-eslint/no-extra-parens
    const lastVisitedTime = migrateSnapshotEnvelope?.snapshottedEventCreatedAt ?? originOfTime
    const pendingEvents = await this.loadEventStreamSince(entityName, entityID, lastVisitedTime)

    if (pendingEvents.length <= 0) {
      return migrateSnapshotEnvelope
    } else {
      this.logger.debug(
        `[EventStore#fetchEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
      )
      let newEntitySnapshot = migrateSnapshotEnvelope
      for (const pendingEvent of pendingEvents) {
        newEntitySnapshot = await this.entityReducer(newEntitySnapshot, pendingEvent)
      }

      this.logger.debug(
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
    this.logger.debug('[EventStore#calculateAndStoreEntitySnapshot] Processing events: ', pendingEnvelopes)
    this.logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Fetching snapshot for entity ${entityName} with ID ${entityID}`
    )
    const latestSnapshotEnvelope = await this.loadLatestSnapshot(entityName, entityID)
    const migrateSnapshotEnvelope = await this.migrateSnapshot(latestSnapshotEnvelope)

    this.logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
    )
    let newEntitySnapshot = migrateSnapshotEnvelope
    for (const pendingEvent of pendingEnvelopes) {
      newEntitySnapshot = await this.entityReducer(newEntitySnapshot, pendingEvent)
    }

    this.logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Reduced new snapshot for entity ${entityName} with ID ${entityID}: `,
      newEntitySnapshot
    )

    if (!newEntitySnapshot) {
      this.logger.debug('New entity snapshot is null. Returning old one (which can also be null)')
      return migrateSnapshotEnvelope
    }

    await this.storeSnapshot(newEntitySnapshot)

    return newEntitySnapshot
  }

  private async storeSnapshot(snapshot: EventEnvelope): Promise<void> {
    this.logger.debug('[EventStore#storeSnapshot] Storing snapshot in the event store:', snapshot)
    return this.provider.events.store([snapshot], this.config, this.logger)
  }

  private loadLatestSnapshot(entityName: string, entityID: UUID): Promise<EventEnvelope | null> {
    this.logger.debug(
      `[EventStore#loadLatestSnapshot] Loading latest snapshot for entity ${entityName} and ID ${entityID}`
    )
    return this.provider.events.latestEntitySnapshot(this.config, this.logger, entityName, entityID)
  }

  private loadEventStreamSince(entityTypeName: string, entityID: UUID, timestamp: string): Promise<EventEnvelope[]> {
    this.logger.debug(
      `[EventStore#loadEventStreamSince] Loading list of pending events for entity ${entityTypeName} with ID ${entityID} since ${timestamp}`
    )
    return this.provider.events.forEntitySince(this.config, this.logger, entityTypeName, entityID, timestamp)
  }

  private async entityReducer(
    latestSnapshot: EventEnvelope | null,
    eventEnvelope: EventEnvelope
  ): Promise<EventEnvelope> {
    try {
      this.logger.debug(
        '[EventStore#entityReducer]: Calling reducer with event: ',
        eventEnvelope,
        ' and entity snapshot ',
        latestSnapshot
      )
      const eventMetadata = this.config.events[eventEnvelope.typeName]
      const migratedEventEnvelope = await this.migrator.migrate(eventEnvelope)
      const eventInstance = createInstance(eventMetadata.class, migratedEventEnvelope.value)
      const entityMetadata = this.config.entities[migratedEventEnvelope.entityTypeName]
      const snapshotInstance = latestSnapshot ? createInstance(entityMetadata.class, latestSnapshot.value) : null
      let newEntity: any
      try {
        newEntity = this.reducerForEvent(migratedEventEnvelope.typeName)(eventInstance, snapshotInstance)
      } catch (e) {
        const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(this.config, this.logger)
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
      this.logger.debug('[EventStore#entityReducer]: Reducer result: ', newSnapshot)
      return newSnapshot
    } catch (e) {
      this.logger.error('Error when calling reducer', e)
      throw e
    }
  }

  private reducerForEvent(eventName: string): Function {
    const reducerMetadata = this.config.reducers[eventName]
    if (!reducerMetadata) {
      throw new InvalidParameterError(`No reducer registered for event ${eventName}`)
    } else {
      try {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const reducer = (reducerMetadata.class as any)[reducerMetadata.methodName]
        this.logger.debug(
          `[EventStore#reducerForEvent] Found reducer for event ${eventName}: "${reducerMetadata.class.name}.${reducerMetadata.methodName}"`
        )
        return reducer
      } catch {
        throw new Error(`Couldn't load the Entity class ${reducerMetadata.class.name}`)
      }
    }
  }

  private async migrateSnapshot(eventEnvelope: EventEnvelope | null): Promise<EventEnvelope | null> {
    if (eventEnvelope) {
      const migratedEventEnvelope = await this.migrator.migrate(eventEnvelope)
      if (eventEnvelope.version !== migratedEventEnvelope.version) {
        await this.storeSnapshot(migratedEventEnvelope)
      }
      return migratedEventEnvelope
    }
    return null
  }
}
