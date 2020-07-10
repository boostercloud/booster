import { EventEnvelope } from '@boostercloud/framework-types'
import { CosmosClient, ItemResponse, SqlQuerySpec } from '@azure/cosmos'
import { BoosterConfig, Logger, UUID } from '@boostercloud/framework-types'
import { eventStorePartitionKeyAttribute, eventStoreSortKeyAttribute } from '../constants'
import { partitionKeyForEvent } from './partition-keys'
import { Context } from '@azure/functions'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(context: Context): Array<EventEnvelope> {
  return context.bindings.rawEvent.map(
    (event: any): EventEnvelope => {
      return event as EventEnvelope
    }
  )
}

export async function readEntityEventsSince(
  cosmosDb: CosmosClient | any,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime
  const querySpec: SqlQuerySpec = {
    query: `SELECT * FROM c WHERE c["${eventStorePartitionKeyAttribute}"] = @partitionKey AND c["${eventStoreSortKeyAttribute}"] > @fromTime ORDER BY c["${eventStoreSortKeyAttribute}"] DESC`,
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
  return resources.map((resource: any) => {
    return resource as EventEnvelope
  })
}

export async function readEntityLatestSnapshot(
  cosmosDb: CosmosClient | any,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.eventsStore)
    .items.query({
      query: `SELECT * FROM c WHERE c["${eventStorePartitionKeyAttribute}"] = @partitionKey ORDER BY c["${eventStoreSortKeyAttribute}"] DESC OFFSET 0 LIMIT 1`,
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
  cosmosDb: CosmosClient | any,
  eventEnvelopes: Array<EventEnvelope>,
  config: BoosterConfig,
  logger: Logger
): Promise<void> {
  logger.debug('[EventsAdapter#storeEvents] Storing EventEnvelopes with eventEnvelopes:', eventEnvelopes)
  const events: Array<Promise<ItemResponse<any>>> = eventEnvelopes.map((eventEnvelope) => {
    return cosmosDb
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.eventsStore)
      .items.create({
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
