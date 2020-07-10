import { Logger, BoosterConfig, EventEnvelope } from '@boostercloud/framework-types'
import { EventRegistry } from '..'
import { UserApp, UUID } from '@boostercloud/framework-types/dist'
import * as path from 'path'

const userProject: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: Array<any>): Array<EventEnvelope> {
  return rawEvents.map((event) => event as EventEnvelope)
}

export async function readEntityEventsSince(
  eventsStream: EventRegistry,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime

  const query: object = {
    entityID: entityID,
    entityTypeName: entityTypeName,
    createdAt: {
      $gt: fromTime,
    },
  }

  return (await eventsStream.query(query)) as Array<EventEnvelope>
}

export async function readEntityLatestSnapshot(
  eventsStream: EventRegistry,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const query: object = {
    entityID: entityID,
    entityTypeName: entityTypeName,
    kind: 'snapshot',
  }

  const snapshot = (await eventsStream.queryLatest(query)) as EventEnvelope

  if (snapshot) {
    logger.debug(
      `[EventsAdapter#readEntityLatestSnapshot] Snapshot found for entity ${entityTypeName} with ID ${entityID}:`,
      snapshot
    )
    return snapshot as EventEnvelope
  } else {
    logger.debug(
      `[EventsAdapter#readEntityLatestSnapshot] No snapshot found for entity ${entityTypeName} with ID ${entityID}.`
    )
    return null
  }
}

export async function storeEvents(
  eventsStream: EventRegistry,
  eventEnvelopes: Array<EventEnvelope>,
  _config: BoosterConfig,
  logger: Logger
): Promise<void> {
  logger.info('Publishing the following events:', eventEnvelopes)

  const storeEventPromises = eventEnvelopes.map((envelop: EventEnvelope) => eventsStream.store(envelop))
  await Promise.all(storeEventPromises)

  await userProject.boosterEventDispatcher(eventEnvelopes)
}
