/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  EventEnvelope,
  EntitySnapshotEnvelope,
  EventSearchParameters,
  EventSearchResponse,
  PaginatedEntitiesIdsResult,
  UserApp,
  UUID,
  NonPersistedEventEnvelope,
  NonPersistedEntitySnapshotEnvelope,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '../services/event-registry'
import { RedisAdapter } from '../services/redis-adapter'

export const rawToEnvelopes = (events: Array<unknown>): Array<EventEnvelope> => events as Array<EventEnvelope>

export const store = async (
  registry: EventRegistry,
  userApp: UserApp,
  events: Array<NonPersistedEventEnvelope>,
  config: BoosterConfig
): Promise<Array<EventEnvelope>> => {
  const logger = getLogger(config, 'events-adapter#store')
  const persistableEvents = []
  for (const envelope of events) {
    logger.debug('Storing event envelope', envelope)
    const persistedEvent = await registry.storeEvent(config, envelope)
    persistableEvents.push(persistedEvent)
  }
  await userApp.boosterEventDispatcher(events)
  return persistableEvents
}

export const storeSnapshot = async (
  registry: EventRegistry,
  snapshotEnvelope: NonPersistedEntitySnapshotEnvelope,
  config: BoosterConfig
): Promise<EntitySnapshotEnvelope> => {
  const logger = getLogger(config, 'events-adapter#storeSnapshot')
  logger.debug('Storing snapshot envelope', snapshotEnvelope)
  return await registry.storeSnapshot(config, snapshotEnvelope)
}

const isNewerThan = (isoString: string) => (key: string) => {
  const keyIsoString = key.split(RedisAdapter.keySeparator)[5]
  if (!keyIsoString) throw new Error(`No ISO string in key ${key}`)
  return new Date(keyIsoString) > new Date(isoString)
}

export const forEntitySince = async (
  registry: EventRegistry,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> => {
  const originOfTime = new Date(0).toISOString()
  const fromTime = since ?? originOfTime
  const query = {
    keyQuery: ['ee', entityTypeName, entityID, 'event'].join(RedisAdapter.keySeparator),
    keyPredicate: isNewerThan(fromTime),
    valuePredicate: () => true,
    sortBy: (a: EventEnvelope, b: EventEnvelope) => a.createdAt.localeCompare(b.createdAt),
  }
  const queryResult = await registry.query(config, query)
  return queryResult as Array<EventEnvelope>
}

export async function latestEntitySnapshot(
  registry: EventRegistry,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID
): Promise<EntitySnapshotEnvelope | undefined> {
  const logger = getLogger(config, 'events-adapter#latestEntitySnapshot')
  const query = {
    keyQuery: ['ee', entityTypeName, entityID, 'snapshot'].join(RedisAdapter.keySeparator),
    keyPredicate: () => true,
    valuePredicate: () => true,
    sortBy: (a: EventEnvelope, b: EventEnvelope) => a.createdAt.localeCompare(b.createdAt),
  }
  const snapshot = (await registry.queryLatest(config, query)) as EntitySnapshotEnvelope

  if (snapshot) {
    logger.debug(
      `[EventsAdapter#latestEntitySnapshot] Snapshot found for entity ${entityTypeName} with ID ${entityID}:`,
      snapshot
    )
    return snapshot as EntitySnapshotEnvelope
  } else {
    logger.debug(
      `[EventsAdapter#latestEntitySnapshot] No snapshot found for entity ${entityTypeName} with ID ${entityID}.`
    )
    return undefined
  }
}

export const search = (
  _registry: EventRegistry,
  _config: BoosterConfig,
  _filters: EventSearchParameters
): Promise<Array<EventSearchResponse>> => {
  throw new Error('eventsAdapter#search: Not implemented yet')
}

export async function searchEntitiesIds(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  limit: number,
  afterCursor: Record<string, string> | undefined,
  entityTypeName: string
): Promise<PaginatedEntitiesIdsResult> {
  throw new Error('eventsAdapter#searchEntitiesIds: Not implemented yet')
}
