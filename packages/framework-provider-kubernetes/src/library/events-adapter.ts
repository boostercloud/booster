/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  EventEnvelope,
  EventSearchParameters,
  EventSearchResponse,
  UserApp,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '../services/event-registry'
import { RedisAdapter } from '../services/redis-adapter'

export const rawToEnvelopes = (events: Array<unknown>): Array<EventEnvelope> => events as Array<EventEnvelope>

export const store = async (
  registry: EventRegistry,
  userApp: UserApp,
  events: Array<EventEnvelope>,
  config: BoosterConfig
): Promise<void> => {
  const logger = getLogger(config, 'events-adapter#store')
  for (const envelope of events) {
    logger.debug('Storing event envelope', envelope)
    await registry.store(config, envelope)
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
  return queryResult
}

export async function latestEntitySnapshot(
  registry: EventRegistry,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const logger = getLogger(config, 'events-adapter#latestEntitySnapshot')
  const query = {
    keyQuery: ['ee', entityTypeName, entityID, 'snapshot'].join(RedisAdapter.keySeparator),
    keyPredicate: () => true,
    valuePredicate: () => true,
    sortBy: (a: EventEnvelope, b: EventEnvelope) => a.createdAt.localeCompare(b.createdAt),
  }
  const snapshot = (await registry.queryLatest(config, query)) as EventEnvelope

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
  _filters: EventSearchParameters
): Promise<Array<EventSearchResponse>> => {
  throw new Error('eventsAdapter#search: Not implemented yet')
}
