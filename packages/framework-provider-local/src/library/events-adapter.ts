import {
  UUID,
  UserApp,
  Logger,
  BoosterConfig,
  EventEnvelope,
  OptimisticConcurrencyUnexpectedVersionError
} from '@boostercloud/framework-types'
import { retryIfError } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '..'


// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: Array<unknown>): Array<EventEnvelope> {
  return rawEvents.map((event) => event as EventEnvelope)
}

export async function readEntityEventsSince(
  eventRegistry: EventRegistry,
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
  const result: Array<EventEnvelope> = await eventRegistry.query(query)

  logger.debug(
    `[EventsAdapter#readEntityEventsSince] Loaded events for entity ${entityTypeName} with ID ${entityID} with result:`,
    result
  )
  return result
}

export async function readEntityLatestSnapshot(
  eventRegistry: EventRegistry,
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

  const snapshot = (await eventRegistry.queryLatest(query)) as EventEnvelope

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
  userApp: UserApp,
  eventRegistry: EventRegistry,
  eventEnvelopes: Array<EventEnvelope>,
  _config: BoosterConfig,
  logger: Logger
): Promise<void> {
  logger.debug('[EventsAdapter#storeEvents] Storing the following event envelopes:', eventEnvelopes)
  for (const eventEnvelope of eventEnvelopes) {
    await retryIfError(
      logger,
      () => persistEvent(eventRegistry, eventEnvelope),
      OptimisticConcurrencyUnexpectedVersionError
    )
  }
  logger.debug('[EventsAdapter#storeEvents] EventEnvelopes stored')

  await userApp.boosterEventDispatcher(eventEnvelopes)
}

async function persistEvent(
  eventRegistry: EventRegistry,
  eventEnvelope: EventEnvelope
): Promise<void> {
  try {
    await eventRegistry.store(eventEnvelope)
  } catch (e) {
    //TODO check the exception raised when there is a write error, to implement
    //Optimistic Concurrency
    //if (e.name == 'TODO') { 
    //  throw new OptimisticConcurrencyUnexpectedVersionError(e.message)
    //}
    throw e
  }
}