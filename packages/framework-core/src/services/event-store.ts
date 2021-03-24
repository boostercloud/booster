import {
  BoosterConfig,
  ProviderLibrary,
  Logger,
  UUID,
  EventEnvelope,
  InvalidParameterError,
} from '@boostercloud/framework-types'

const originOfTime = new Date(0).toISOString() // Unix epoch

export class EventStore {
  private config: BoosterConfig
  private provider: ProviderLibrary
  private logger: Logger

  public constructor(config: BoosterConfig, logger: Logger) {
    this.config = config
    this.provider = config.provider
    this.logger = logger
  }

  public async fetchEntitySnapshot(entityName: string, entityID: UUID, at?: string): Promise<EventEnvelope | null> {
    this.logger.debug(`[EventStore#fetchEntitySnapshot] Fetching snapshot for entity ${entityName} with ID ${entityID}`)
    const snapshotEnvelope = await this.loadSnapshot(entityName, entityID, at)

    // eslint-disable-next-line @typescript-eslint/no-extra-parens
    const lastVisitedTime = snapshotEnvelope?.snapshottedEventCreatedAt ?? originOfTime

    const pendingEvents = await this.loadEventStream(entityName, entityID, lastVisitedTime, at)

    if (pendingEvents.length <= 0) {
      return snapshotEnvelope
    } else {
      this.logger.debug(
        `[EventStore#fetchEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
      )
      const newEntitySnapshot = pendingEvents.reduce(this.entityReducer.bind(this), snapshotEnvelope)
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

    this.logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
    )
    const newEntitySnapshot = pendingEnvelopes.reduce(this.entityReducer.bind(this), latestSnapshotEnvelope)
    this.logger.debug(
      `[EventStore#calculateAndStoreEntitySnapshot] Reduced new snapshot for entity ${entityName} with ID ${entityID}: `,
      newEntitySnapshot
    )

    if (!newEntitySnapshot) {
      this.logger.debug('New entity snapshot is null. Returning old one (which can also be null)')
      return latestSnapshotEnvelope
    }

    await this.storeSnapshot(newEntitySnapshot)

    return newEntitySnapshot
  }

  private async storeSnapshot(snapshot: EventEnvelope): Promise<void> {
    this.logger.debug('[EventStore#storeSnapshot] Storing snapshot in the event store:', snapshot)
    return this.provider.events.store([snapshot], this.config, this.logger)
  }

  /**
   * Get the latest snapshot available.
   * Optionally, you can retrieve the latest snapshot available at a specific point in time.
   * @param entityName The entity class we want the snapshot from
   * @param entityID The specific entity id we want the snapshot from
   * @param at (optional) Retrieve a snapshot from the past at a specific point in time
   */
  private loadSnapshot(entityName: string, entityID: UUID, at?: string): Promise<EventEnvelope | null> {
    this.logger.debug(
      `[EventStore#loadLatestSnapshot] Loading latest snapshot for entity ${entityName} and ID ${entityID}`
    )
    return this.provider.events.latestEntitySnapshot(this.config, this.logger, entityName, entityID, at)
  }

  /**
   * Retrieves events from a specific point in time.
   * Optionally, it can also retrieve events in a time range: from < events.createdAt < to
   * @param entityTypeName The entity class we want to retrieve events from
   * @param entityID The specific entity instance we want to retrieve events from
   * @param from The point in time from which the events are going to be retrieved
   * @param to (optional) At which point in time we stop retrieving events
   * Full example: Get events from the Cart entity with id 1112 from 1 month ago to 2 weeks ago
   */
  private loadEventStream(entityTypeName: string, entityID: UUID, from: string, to?: string): Promise<EventEnvelope[]> {
    this.logger.debug(
      `[EventStore#loadEventStreamSince] Loading list of pending events for entity ${entityTypeName} with ID ${entityID} since ${from}`
    )
    return this.provider.events.forEntitySince(this.config, this.logger, entityTypeName, entityID, from, to)
  }

  private entityReducer(latestSnapshot: EventEnvelope | null, eventEnvelope: EventEnvelope): EventEnvelope {
    try {
      this.logger.debug(
        '[EventStore#entityReducer]: Calling reducer with event: ',
        eventEnvelope,
        ' and entity snapshot ',
        latestSnapshot
      )
      const snapshotValue = latestSnapshot ? latestSnapshot.value : null
      const newEntity = this.reducerForEvent(eventEnvelope.typeName)(eventEnvelope.value, snapshotValue)
      const newSnapshot: EventEnvelope = {
        version: this.config.currentVersionFor(eventEnvelope.entityTypeName),
        kind: 'snapshot',
        requestID: eventEnvelope.requestID,
        entityID: eventEnvelope.entityID,
        entityTypeName: eventEnvelope.entityTypeName,
        typeName: eventEnvelope.entityTypeName,
        value: newEntity,
        createdAt: new Date().toISOString(), // TODO: This could be overridden by the provider. We should not set it. Ensure all providers set it
        snapshottedEventCreatedAt: eventEnvelope.createdAt,
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
}
