/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  EventEnvelope,
  EventFilter,
  EventSearchResponse,
  Logger,
  UserApp,
  UUID,
} from '@boostercloud/framework-types'
import { EventRegistry } from '../services/event-registry'
import { RedisAdapter } from '../services/redis-adapter'

export const rawToEnvelopes = (events: Array<unknown>): Array<EventEnvelope> => events as Array<EventEnvelope>

export const store = async (
  registry: EventRegistry,
  userApp: UserApp,
  events: Array<EventEnvelope>,
  _config: BoosterConfig,
  logger: Logger
): Promise<void> => {
  for (const envelope of events) {
    logger.debug('Storing event envelope', envelope)
    await registry.store(envelope, logger)
  }
  await userApp.boosterEventDispatcher(events)
}

const isNewerThan = (isoString: string) => (key: string) => {
  const keyIsoString = key.split(RedisAdapter.keySeparator)[5]
  if (!keyIsoString) throw new Error(`No ISO string in key ${key}`)
  return new Date(keyIsoString) > new Date(isoString)
}

export const forEntitySince = async (
  registry: EventRegistry,
  _config: BoosterConfig,
  logger: Logger,
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
  const queryResult = await registry.query(query, logger)
  return queryResult
}

export async function latestEntitySnapshot(
  registry: EventRegistry,
  _config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const query = {
    keyQuery: ['ee', entityTypeName, entityID, 'snapshot'].join(RedisAdapter.keySeparator),
    keyPredicate: () => true,
    valuePredicate: () => true,
    sortBy: (a: EventEnvelope, b: EventEnvelope) => a.createdAt.localeCompare(b.createdAt),
  }
  const snapshot = (await registry.queryLatest(query, logger)) as EventEnvelope

  if (snapshot) {
    logger.debug(
      `[EventsAdapter#latestEntitySnapshot] Snapshot found for entity ${entityTypeName} with ID ${entityID}:`,
      snapshot
    )
    return snapshot as EventEnvelope
  } else {
    logger.debug(
      `[EventsAdapter#latestEntitySnapshot] No snapshot found for entity ${entityTypeName} with ID ${entityID}.`
    )
    return null
  }
}

export const search = (
  _registry: EventRegistry,
  _config: BoosterConfig,
  _logger: Logger,
  _filters: EventFilter
): Promise<Array<EventSearchResponse>> => {
  throw new Error('eventsAdapter#search: Not implemented yet')
}
