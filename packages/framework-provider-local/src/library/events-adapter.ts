import {
  UUID,
  UserApp,
  BoosterConfig,
  EventEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
  EntitySnapshotEnvelope,
  NonPersistedEventEnvelope,
  NonPersistedEntitySnapshotEnvelope,
} from '@boostercloud/framework-types'
import { retryIfError, getLogger } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '..'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: Array<unknown>): Array<EventEnvelope> {
  return rawEvents.map((event) => event as EventEnvelope)
}

export async function readEntityEventsSince(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-adapter#readEntityEventsSince')
  const fromTime = since ? since : originOfTime

  const query = {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind: 'event',
    createdAt: {
      $gt: fromTime,
    },
    deletedAt: { $exists: false },
  }
  const result = await eventRegistry.query(query)

  logger.debug(`Loaded events for entity ${entityTypeName} with ID ${entityID} with result:`, result)
  return result as Array<EventEnvelope>
}

export async function readEntityLatestSnapshot(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID
): Promise<EntitySnapshotEnvelope | undefined> {
  const logger = getLogger(config, 'events-adapter#readEntityLatestSnapshot')
  const query = {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind: 'snapshot',
  }

  const snapshot = await eventRegistry.queryLatestSnapshot(query)

  if (snapshot) {
    logger.debug(`Snapshot found for entity ${entityTypeName} with ID ${entityID}:`, snapshot)
    return snapshot as EntitySnapshotEnvelope
  } else {
    logger.debug(`No snapshot found for entity ${entityTypeName} with ID ${entityID}.`)
    return undefined
  }
}

export async function storeEvents(
  userApp: UserApp,
  eventRegistry: EventRegistry,
  nonPersistedEventEnvelopes: Array<NonPersistedEventEnvelope>,
  config: BoosterConfig
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-adapter#storeEvents')
  logger.debug('Storing the following event envelopes:', nonPersistedEventEnvelopes)
  const persistedEventEnvelopes: Array<EventEnvelope> = []
  for (const nonPersistedEventEnvelope of nonPersistedEventEnvelopes) {
    const persistableEventEnvelope = {
      ...nonPersistedEventEnvelope,
      createdAt: new Date().toISOString(),
    }
    await retryIfError(
      async () => await persistEvent(eventRegistry, persistableEventEnvelope),
      OptimisticConcurrencyUnexpectedVersionError
    )
    persistedEventEnvelopes.push(persistableEventEnvelope)
  }
  logger.debug('EventEnvelopes stored: ', persistedEventEnvelopes)

  await userApp.boosterEventDispatcher(persistedEventEnvelopes)
  return persistedEventEnvelopes
}

export async function storeSnapshot(
  eventRegistry: EventRegistry,
  snapshotEnvelope: NonPersistedEntitySnapshotEnvelope,
  config: BoosterConfig
): Promise<EntitySnapshotEnvelope> {
  const logger = getLogger(config, 'events-adapter#storeSnapshot')
  logger.debug('Storing the following snapshot envelope:', snapshotEnvelope)
  const persistableEntitySnapshot = {
    ...snapshotEnvelope,
    createdAt: snapshotEnvelope.snapshottedEventCreatedAt,
    persistedAt: new Date().toISOString(),
  }
  await retryIfError(() => eventRegistry.store(persistableEntitySnapshot), OptimisticConcurrencyUnexpectedVersionError)
  logger.debug('Snapshot stored')
  return persistableEntitySnapshot
}

/**
 * Dummy method that'll always return true, since local provider won't be tracking dispatched events
 */
export async function storeDispatchedEvent() {
  return true
}

async function persistEvent(eventRegistry: EventRegistry, eventEnvelope: EventEnvelope): Promise<void> {
  await eventRegistry.store(eventEnvelope)
}
