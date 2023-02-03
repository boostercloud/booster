import { CosmosClient, SqlQuerySpec } from '@azure/cosmos'
import {
  EventEnvelope,
  BoosterConfig,
  UUID,
  EntitySnapshotEnvelope,
  NonPersistedEventEnvelope,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { eventsStoreAttributes } from '../constants'
import { partitionKeyForEvent, partitionKeyForSnapshot } from './partition-keys'
import { Context } from '@azure/functions'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(context: Context): Array<EventEnvelope> {
  return context.bindings.rawEvent as Array<EventEnvelope>
}

export async function readEntityEventsSince(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime
  const querySpec: SqlQuerySpec = {
    query:
      `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
      `AND c["${eventsStoreAttributes.sortKey}"] > @fromTime ORDER BY c["${eventsStoreAttributes.sortKey}"] ASC`,
    parameters: [
      {
        name: '@partitionKey',
        value: partitionKeyForEvent(entityTypeName, entityID),
      },
      {
        name: '@fromTime',
        value: fromTime,
      },
    ],
  }
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.eventsStore)
    .items.query(querySpec)
    .fetchAll()
  return resources as Array<EventEnvelope>
}

export async function readEntityLatestSnapshot(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID
): Promise<EntitySnapshotEnvelope | undefined> {
  const logger = getLogger(config, 'events-adapter#readEntityLatestSnapshot')
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.eventsStore)
    .items.query({
      query:
        `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
        `ORDER BY c["${eventsStoreAttributes.sortKey}"] DESC OFFSET 0 LIMIT 1`,
      parameters: [
        {
          name: '@partitionKey',
          value: partitionKeyForSnapshot(entityTypeName, entityID),
        },
      ],
    })
    .fetchAll()

  const snapshot = resources[0]
  if (snapshot) {
    logger.debug(
      `[EventsAdapter#readEntityLatestSnapshot] Snapshot found for entity ${entityTypeName} with ID ${entityID}:`,
      snapshot
    )
    return snapshot as EntitySnapshotEnvelope
  } else {
    logger.debug(
      `[EventsAdapter#readEntityLatestSnapshot] No snapshot found for entity ${entityTypeName} with ID ${entityID}.`
    )
    return undefined
  }
}

export async function storeEvents(
  cosmosDb: CosmosClient,
  eventEnvelopes: Array<NonPersistedEventEnvelope>,
  config: BoosterConfig
): Promise<void> {
  const logger = getLogger(config, 'events-adapter#storeEvents')
  logger.debug('[EventsAdapter#storeEvents] Storing EventEnvelopes with eventEnvelopes:', eventEnvelopes)
  for (const eventEnvelope of eventEnvelopes) {
    const persistableEvent: EventEnvelope = {
      ...eventEnvelope,
      persistedAt: new Date().toISOString(),
    }
    await cosmosDb
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.eventsStore)
      .items.create({
        ...persistableEvent,
        [eventsStoreAttributes.partitionKey]: partitionKeyForEvent(
          eventEnvelope.entityTypeName,
          eventEnvelope.entityID
        ),
        [eventsStoreAttributes.sortKey]: persistableEvent.persistedAt,
      })
  }
  logger.debug('[EventsAdapter#storeEvents] EventEnvelope stored')
}

export async function storeSnapshot(
  cosmosDb: CosmosClient,
  snapshotEnvelope: EntitySnapshotEnvelope,
  config: BoosterConfig
): Promise<void> {
  const logger = getLogger(config, 'events-adapter#storeSnapshot')
  logger.debug('[EventsAdapter#storeSnapshot] Storing snapshot with snapshotEnvelope:', snapshotEnvelope)

  const partitionKey = partitionKeyForSnapshot(snapshotEnvelope.entityTypeName, snapshotEnvelope.entityID)
  /**
   * The sort key of the snapshot matches the sort key of the last event that generated it.
   * Entity snapshots can be potentially created by competing processes, so we need to make sure
   * that no matter what snapshot we find, we can always rebuild the correct state by
   * replaying all the events that happened after the snapshot we take as the origin.
   * This way of storing the data makes snapshot caching an idempotent operation, and allows us to
   * aggressively caching snapshots without worrying about insertion order.
   */
  const sortKey = snapshotEnvelope.snapshottedEventPersistedAt

  await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.eventsStore)
    .items.create({
      ...snapshotEnvelope,
      [eventsStoreAttributes.partitionKey]: partitionKey,
      [eventsStoreAttributes.sortKey]: sortKey,
    })

  logger.debug('[EventsAdapter#storeSnapshot] Snapshot stored')
}
