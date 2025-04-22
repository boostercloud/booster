import { CosmosClient, SqlQuerySpec } from '@azure/cosmos'
import {
  BoosterConfig,
  EntitySnapshotEnvelope,
  EventEnvelope,
  NonPersistedEntitySnapshotEnvelope,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { eventsStoreAttributes } from '../constants'
import { partitionKeyForEvent, partitionKeyForSnapshot } from './partition-keys'
import { Context } from '@azure/functions'

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
  const logger = getLogger(config, 'events-adapter#readEntityEventsSince')
  const fromTime = since ? since : originOfTime
  const querySpec: SqlQuerySpec = {
    query:
      `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
      `AND c["${eventsStoreAttributes.sortKey}"] > @fromTime ` +
      'AND NOT IS_DEFINED(c["deletedAt"]) ' +
      `ORDER BY c["${eventsStoreAttributes.sortKey}"] ASC`,
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
  logger.debug(`Loaded events for entity ${entityTypeName} with ID ${entityID} with result:`, resources)
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

export async function storeSnapshot(
  cosmosDb: CosmosClient,
  snapshotEnvelope: NonPersistedEntitySnapshotEnvelope,
  config: BoosterConfig
): Promise<EntitySnapshotEnvelope> {
  const logger = getLogger(config, 'events-adapter#storeSnapshot')
  logger.debug('Storing snapshot with snapshotEnvelope:', snapshotEnvelope)

  const partitionKey = partitionKeyForSnapshot(snapshotEnvelope.entityTypeName, snapshotEnvelope.entityID)
  /**
   * The sort key of the snapshot matches the sort key of the last event that generated it.
   * Entity snapshots can be potentially created by competing processes, and this way
   * of storing the data makes snapshot creation an idempotent operation, allowing us to
   * aggressively cache snapshots. If the snapshot already exists, it will be silently overwritten.
   */
  const sortKey = snapshotEnvelope.snapshottedEventCreatedAt

  const container = cosmosDb.database(config.resourceNames.applicationStack).container(config.resourceNames.eventsStore)

  /* TODO: As the sortKey is not part of an unique key in the table by default, there's no easy way to
   * ensure that a snapshot is created only once, so we need to check if it exists first.
   * This is not ideal, but conditional writes doesn't seem to have a simple solution in CosmosDB at
   * the moment. We should revisit this in the future.
   *
   * Notice that while this implementation can potentially fail to prevent an extra snapshot to be created,
   * the existence of such extra snapshot has no impact on the way Booster works. This check is here
   * because we want to avoid snapshots to be created out of control in scenarios where a big number
   * of event handlers are requesting the latest state of the same entity.
   */
  const { resources } = await container.items
    .query({
      query: 'SELECT * FROM c WHERE c.partitionKey = @partitionKey AND c.sortKey = @sortKey',
      parameters: [
        {
          name: '@partitionKey',
          value: partitionKey,
        },
        {
          name: '@sortKey',
          value: sortKey,
        },
      ],
    })
    .fetchAll()

  if (resources.length > 0) {
    throw new Error('Snapshot already exists. skipping...')
  }

  const persistableEntitySnapshot: EntitySnapshotEnvelope = {
    ...snapshotEnvelope,
    createdAt: snapshotEnvelope.snapshottedEventCreatedAt,
    persistedAt: new Date().toISOString(),
  }
  await container.items.create({
    ...persistableEntitySnapshot,
    [eventsStoreAttributes.partitionKey]: partitionKey,
    [eventsStoreAttributes.sortKey]: sortKey,
  })
  logger.debug('Snapshot stored', snapshotEnvelope)
  return persistableEntitySnapshot
}

export async function storeDispatchedEvent(
  cosmosDb: CosmosClient,
  eventEnvelope: EventEnvelope,
  config: BoosterConfig
): Promise<boolean> {
  const logger = getLogger(config, 'events-adapter#storeDispatchedEvent')
  logger.debug('[EventsAdapter#storeDispatchedEvent] Storing EventEnvelope for event with ID: ', eventEnvelope.id)
  try {
    await cosmosDb
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.dispatchedEventsStore)
      .items.create({
        eventId: eventEnvelope.id,
      })
    return true
  } catch (e) {
    if (e.code === 409) {
      // If an item with the same ID already exists in the container, it will return a 409 status code.
      // See https://learn.microsoft.com/en-us/rest/api/cosmos-db/http-status-codes-for-cosmosdb
      logger.debug('[EventsAdapter#storeDispatchedEvent] Event has already been dispatched', eventEnvelope.id)
      return false
    } else {
      logger.error('[EventsAdapter#storeDispatchedEvent] Error storing dispatched event', e)
      throw e
    }
  }
}
