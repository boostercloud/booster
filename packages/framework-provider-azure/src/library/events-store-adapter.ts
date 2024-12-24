import { BoosterConfig, EventEnvelope, NonPersistedEventEnvelope } from '@boostercloud/framework-types'
import { CosmosClient, CreateOperationInput, JSONObject, OperationResponse, Response } from '@azure/cosmos'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { partitionKeyForEvent } from './partition-keys'
import { eventsStoreAttributes } from '../constants'

type NonPersistedEventEnvelopePerPartitionKey = Record<string, Array<NonPersistedEventEnvelope>>

const DEFAULT_CHUNK_SIZE = 100

export async function storeEvents(
  cosmosDb: CosmosClient,
  eventEnvelopes: Array<NonPersistedEventEnvelope>,
  config: BoosterConfig
): Promise<Array<EventEnvelope>> {
  if (config.azureConfiguration?.enableBatching) {
    return storeEventsInBatch(cosmosDb, eventEnvelopes, config)
  }

  const logger = getLogger(config, 'store-events-adapter#storeEvents')
  logger.debug('Storing EventEnvelopes with eventEnvelopes:', eventEnvelopes)

  const persistableEvents = []
  for (const eventEnvelope of eventEnvelopes) {
    const persistableEvent: EventEnvelope = {
      ...eventEnvelope,
      createdAt: new Date().toISOString(),
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
        [eventsStoreAttributes.sortKey]: persistableEvent.createdAt,
      })
    persistableEvents.push(persistableEvent)
  }
  logger.debug('EventEnvelopes stored:')
  return persistableEvents
}

/**
 * Limits: The Azure Cosmos DB request size limit constrains the size of the TransactionalBatch payload to not exceed 2 MB,
 * and the maximum execution time is 5 seconds. There's a current limit of 100 operations per TransactionalBatch to ensure
 * the performance is as expected and within SLAs.
 *
 * Errors: If there's a failure, the failed operation will have a status code of its corresponding error. All the other
 * operations will have a 424 status code (failed dependency). The status code enables one to identify the cause of transaction failure.
 *
 * @param cosmosDb
 * @param eventEnvelopes
 * @param config
 */
async function storeEventsInBatch(
  cosmosDb: CosmosClient,
  eventEnvelopes: Array<NonPersistedEventEnvelope>,
  config: BoosterConfig
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'store-events-adapter#storeEventsInBatch')
  logger.debug('Storing EventEnvelopes with eventEnvelopes:', eventEnvelopes)
  const envelopesWithCreatedAt: Array<EventEnvelope> = []

  const eventsPerPartitionKey = eventEnvelopes.reduce(groupByPartitionKey, {})
  for (const [partitionKey, eventsInPartitionKey] of Object.entries(eventsPerPartitionKey)) {
    const chunksOfEventsInPartitionKey = chunkEvents(eventsInPartitionKey, DEFAULT_CHUNK_SIZE)
    for (const eventListInChunk of chunksOfEventsInPartitionKey) {
      const eventEnvelopesChunkWithCreatedAt = toEventEnvelopes(eventListInChunk)
      envelopesWithCreatedAt.push(...eventEnvelopesChunkWithCreatedAt)
      const inputOperations: Array<CreateOperationInput> = toInputOperations(eventEnvelopesChunkWithCreatedAt, config)
      const batchResponse = await batchEvents(cosmosDb, config, inputOperations, partitionKey)

      // Batch is transactional and will roll back all operations if one fails
      if (batchResponse?.code !== 200) {
        logger.error(`An error ocurred storing a batch of events. Batch response: ${JSON.stringify(batchResponse)}`)
        throw new Error(`Error ${batchResponse.substatus} storing events: ${JSON.stringify(batchResponse)}`)
      }
      const result = batchResponse.result
      logger.debug(`EventEnvelopes with ${partitionKey} stored: ${result}`)
    }
  }

  return envelopesWithCreatedAt
}

function toEventEnvelopes(events: Array<NonPersistedEventEnvelope>): Array<EventEnvelope> {
  return events.map((eventEnvelope: NonPersistedEventEnvelope) => ({
    ...eventEnvelope,
    createdAt: new Date().toISOString(),
  }))
}

function toInputOperations(
  eventEnvelopesChunkWithCreatedAt: Array<EventEnvelope>,
  config: BoosterConfig
): Array<CreateOperationInput> {
  return eventEnvelopesChunkWithCreatedAt.map((eventEnvelopeWithCreatedAt) => {
    const body: JSONObject = storableResource(config, eventEnvelopeWithCreatedAt)
    return {
      operationType: 'Create',
      resourceBody: body,
    }
  })
}

async function batchEvents(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  inputOperations: Array<CreateOperationInput>,
  partitionKey: string
): Promise<Response<OperationResponse[]>> {
  const logger = getLogger(config, 'store-events-adapter#batchEvents')
  try {
    logger.debug('Storing EventEnvelopes with inputOperations:', inputOperations)
    return await cosmosDb
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.eventsStore)
      .items.batch(inputOperations, partitionKey)
  } catch (e) {
    logger.error('Unexpected error storing events', e)
    throw e
  }
}

function groupByPartitionKey(
  eventsPerPartitionKey: NonPersistedEventEnvelopePerPartitionKey,
  event: NonPersistedEventEnvelope
): NonPersistedEventEnvelopePerPartitionKey {
  const partitionKey: string = partitionKeyForEvent(event.entityTypeName, event.entityID)
  if (!eventsPerPartitionKey[partitionKey]) {
    eventsPerPartitionKey[partitionKey] = []
  }
  eventsPerPartitionKey[partitionKey].push(event)
  return eventsPerPartitionKey
}

/**
 * Batch method expects a JSONObject. JSONObject valid types are: boolean | number | string | null | JSONArray | JSONObject
 * This method tries to build a JSONObject from an EventEnvelope.
 * It could fail if value contains a circular reference or a BigInt value is encountered.
 * @param config
 * @param eventEnvelope
 */
function storableResource(config: BoosterConfig, eventEnvelope: EventEnvelope): JSONObject {
  const logger = getLogger(config, 'store-events-adapter#storableResource')
  try {
    const partitionKey = partitionKeyForEvent(eventEnvelope.entityTypeName, eventEnvelope.entityID)
    const eventEnvelopeWithEntityID = {
      ...eventEnvelope,
      [eventsStoreAttributes.partitionKey]: partitionKey,
      [eventsStoreAttributes.sortKey]: eventEnvelope.createdAt,
    }
    return eventEnvelopeWithEntityID as unknown as JSONObject
  } catch (e) {
    logger.error(`Could not parse eventEnvelope ${eventEnvelope} to JSONObject`, e)
    throw e
  }
}

/**
 * Split events in chunks of size DEFAULT_CHUNK_SIZE
 *
 * @param arr
 * @param size
 */
function chunkEvents(arr: Array<NonPersistedEventEnvelope>, size: number): Array<Array<NonPersistedEventEnvelope>> {
  return Array.from({ length: Math.ceil(arr.length / size) }, (_: NonPersistedEventEnvelope, i: number) =>
    arr.slice(i * size, i * size + size)
  )
}
