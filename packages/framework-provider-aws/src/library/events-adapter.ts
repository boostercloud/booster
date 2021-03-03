/* eslint-disable @typescript-eslint/no-unused-vars */
import { DynamoDBStreamEvent, DynamoDBRecord } from 'aws-lambda'
import { BoosterConfig, EventEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { dynamoDbBatchWriteLimit, eventsStoreAttributes } from '../constants'
import { partitionKeyForEvent } from './partition-keys'
import { Converter } from 'aws-sdk/clients/dynamodb'
import { inChunksOf, waitAndReturn } from '../pagination-helpers'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: DynamoDBStreamEvent): Array<EventEnvelope> {
  return rawEvents.Records.map(
    (record: DynamoDBRecord): EventEnvelope => {
      if (!record.dynamodb?.NewImage) {
        throw new Error('Received a DynamoDB stream event without "NewImage" field. It is required')
      }
      return Converter.unmarshall(record.dynamodb?.NewImage) as EventEnvelope
    }
  )
}

export async function readEntityEventsSince(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime
  const result = await dynamoDB
    .query({
      TableName: config.resourceNames.eventsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey AND ${eventsStoreAttributes.sortKey} > :fromTime`,
      ExpressionAttributeValues: {
        ':partitionKey': partitionKeyForEvent(entityTypeName, entityID),
        ':fromTime': fromTime,
      },
      ScanIndexForward: true, // Ascending order (older timestamps first)
    })
    .promise()
  logger.debug(
    `[EventsAdapter#readEntityEventsSince] Loaded events for entity ${entityTypeName} with ID ${entityID} with result:`,
    result.Items
  )
  return result.Items as Array<EventEnvelope>
}

export async function readEntityLatestSnapshot(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const result = await dynamoDB
    .query({
      TableName: config.resourceNames.eventsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey`,
      ExpressionAttributeValues: {
        ':partitionKey': partitionKeyForEvent(entityTypeName, entityID, 'snapshot'),
      },
      ScanIndexForward: false, // Descending order (newer timestamps first),
      Limit: 1,
    })
    .promise()

  const snapshot = result.Items?.shift()
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
  dynamoDB: DynamoDB.DocumentClient,
  eventEnvelopes: Array<EventEnvelope>,
  config: BoosterConfig,
  logger: Logger
): Promise<void> {
  const batches = inChunksOf(dynamoDbBatchWriteLimit, eventEnvelopes)
  for (const batch of batches) {
    await persistBatch(logger, batch, config, dynamoDB)
  }
}

async function persistBatch(
  logger: Logger,
  batch: EventEnvelope[],
  config: BoosterConfig,
  dynamoDB: DynamoDB.DocumentClient
): Promise<void> {
  logger.debug('[EventsAdapter#storeEvents] Storing EventEnvelopes with eventEnvelopes:', batch)
  const putRequests = []
  for (const eventEnvelope of batch) {
    const msForSortKey = 5
    /* We must wait 5ms before generating a new sort key value
    because if we do it directly, all of them will have the same
    timestamp, meaning that for DynamoDB this is the same item as
    the others, because it has the same key. Making BatchWrite fail.
    */
    const sortKey = await waitAndReturn(() => new Date().toISOString(), msForSortKey)
    putRequests.push({
      PutRequest: {
        Item: {
          ...eventEnvelope,
          [eventsStoreAttributes.partitionKey]: partitionKeyForEvent(
            eventEnvelope.entityTypeName,
            eventEnvelope.entityID,
            eventEnvelope.kind
          ),
          [eventsStoreAttributes.sortKey]: sortKey,
        },
      },
    })
  }
  const params: DynamoDB.DocumentClient.BatchWriteItemInput = {
    RequestItems: {
      [config.resourceNames.eventsStore]: putRequests,
    },
  }
  await dynamoDB.batchWrite(params).promise()
  logger.debug('[EventsAdapter#storeEvents] EventEnvelope stored')
}
