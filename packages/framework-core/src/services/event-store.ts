import {
  BoosterConfig,
  ProviderLibrary,
  Logger,
  UUID,
  EventEnvelope,
  InvalidParameterError,
} from '@boostercloud/framework-types'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const numberOfEventsBetweenSnapshots = 5 // TODO: Move this to Booster configuration
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

  public async fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EventEnvelope | null> {
    this.logger.debug(`[EventStore#fetchEntitySnapshot] Fetching snapshot for entity ${entityName} with ID ${entityID}`)
    const latestSnapshotEnvelope = await this.loadLatestSnapshot(entityName, entityID)

    // eslint-disable-next-line @typescript-eslint/no-extra-parens
    const lastVisitedTime = latestSnapshotEnvelope?.createdAt || originOfTime
    const pendingEvents = await this.loadEventStreamSince(entityName, entityID, lastVisitedTime)

    if (pendingEvents.length <= 0) {
      return latestSnapshotEnvelope
    } else {
      this.logger.debug(
        `[EventStore#fetchEntitySnapshot] Looking for the reducer for entity ${entityName} with ID ${entityID}`
      )
      const newEntitySnapshot = pendingEvents.reduce(this.entityReducer.bind(this), latestSnapshotEnvelope)
      this.logger.debug(
        `[EventStore#fetchEntitySnapshot] Reduced new snapshot for entity ${entityName} with ID ${entityID}: `,
        newEntitySnapshot
      )

      if (newEntitySnapshot && pendingEvents.length >= numberOfEventsBetweenSnapshots) {
        await this.storeSnapshot(newEntitySnapshot)
      }

      return newEntitySnapshot
    }
  }

  private async storeSnapshot(snapshot: EventEnvelope): Promise<void> {
    this.logger.debug(
      `[EventStore#storeSnapshot] Maximum number of events after latest stored snapshot reached (${numberOfEventsBetweenSnapshots}). Storing snapshot in the event store:`,
      snapshot
    )
    return await this.provider.events.store([snapshot], this.config, this.logger)
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
        createdAt: eventEnvelope.createdAt,
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
