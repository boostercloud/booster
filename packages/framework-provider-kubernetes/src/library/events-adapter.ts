/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  EventEnvelope,
  EventFilter,
  EventSearchResponse,
  Logger,
  UUID,
} from '@boostercloud/framework-types'
import { EventRegistry } from '../services/event-registry'

export const rawToEnvelopes = (events: Array<unknown>): Array<EventEnvelope> => events as Array<EventEnvelope>

export const store = async (
  registry: EventRegistry,
  events: Array<EventEnvelope>,
  _config: BoosterConfig,
  logger: Logger
): Promise<void> => {
  for (const envelope of events) {
    logger.debug('Storing event envelope', envelope)
    await registry.store(envelope, logger)
  }
}

export const forEntitySince = async (
  registry: EventRegistry,
  _config: BoosterConfig,
  logger: Logger,
  _entityTypeName: string,
  _entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> => {
  const originOfTime = new Date(0).toISOString()
  const fromTime = since ?? originOfTime
  const query = {
    // TODO: add required stuff to query properly
    createdAt: {
      $gt: fromTime,
    },
  }
  return await registry.query(query, logger)
}

export async function latestEntitySnapshot(
  registry: EventRegistry,
  _config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const query: object = {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind: 'snapshot',
  }

  const snapshot = (await registry.queryLatest(query)) as EventEnvelope

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
