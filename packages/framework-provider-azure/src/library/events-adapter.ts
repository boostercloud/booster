import { EventEnvelope } from '@boostercloud/framework-types'
import { Container, CosmosClient, Database, ItemResponse } from '@azure/cosmos'
import { BoosterConfig, Logger, UUID } from '@boostercloud/framework-types'
import { eventStorePartitionKeyAttribute, eventStoreSortKeyAttribute } from '../constants'
import { partitionKeyForEvent } from './partition-keys'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: Array<any>): Array<EventEnvelope> {
  return rawEvents.map(
    (rawEvent: any): EventEnvelope => {
      return rawEvent as EventEnvelope
    }
  )
}

export async function readEntityEventsSince(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime
  const database: Database = cosmosDb.database(config.resourceNames.applicationStack)
  const container: Container = database.container(config.resourceNames.eventsStore)
  const { resources } = await container.items
    .query({
      query: `SELECT * FROM c where c.${eventStorePartitionKeyAttribute} = @partitionKey AND c.${eventStoreSortKeyAttribute} > @fromTime ORDER BY c.${eventStoreSortKeyAttribute} DESC`,
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
    })
    .fetchAll()
  return resources.map((resource: any) => {
    return resource as EventEnvelope
  })
}

export async function readEntityLatestSnapshot(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const database: Database = cosmosDb.database(config.resourceNames.applicationStack)
  const container: Container = database.container(config.resourceNames.eventsStore)
  const { resources } = await container.items
    .query({
      query: `SELECT * FROM c where c.${eventStorePartitionKeyAttribute} = @partitionKey ORDER BY c.${eventStoreSortKeyAttribute} DESC LIMIT 1`,
      parameters: [
        {
          name: '@partitionKey',
          value: partitionKeyForEvent(entityTypeName, entityID, 'snapshot'),
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
    return snapshot as EventEnvelope
  } else {
    logger.debug(
      `[EventsAdapter#readEntityLatestSnapshot] No snapshot found for entity ${entityTypeName} with ID ${entityID}.`
    )
    return null
  }
}

export async function storeEvents(
  cosmosDb: CosmosClient,
  eventEnvelopes: Array<EventEnvelope>,
  config: BoosterConfig,
  logger: Logger
): Promise<void> {
  logger.debug('[EventsAdapter#storeEvents] Storing EventEnvelopes with eventEnvelopes:', eventEnvelopes)
  const database: Database = cosmosDb.database(config.resourceNames.applicationStack)
  const container: Container = database.container(config.resourceNames.eventsStore)
  const events: Array<Promise<ItemResponse<any>>> = eventEnvelopes.map((eventEnvelope) => {
    return container.items.create({
      ...eventEnvelope,
      [eventStorePartitionKeyAttribute]: partitionKeyForEvent(
        eventEnvelope.entityTypeName,
        eventEnvelope.entityID,
        eventEnvelope.kind
      ),
      [eventStoreSortKeyAttribute]: new Date().toISOString(),
    })
  })
  await Promise.all(events)
  logger.debug('[EventsAdapter#storeEvents] EventEnvelope stored')
}
