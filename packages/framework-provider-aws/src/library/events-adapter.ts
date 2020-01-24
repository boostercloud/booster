import { KinesisStreamEvent, KinesisStreamRecord } from 'aws-lambda'
import { BoosterConfig, EventEnvelope, Logger } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { eventStorePartitionKeyAttributeName, eventStoreSortKeyAttributeName } from '../constants'
import { partitionKeyForEvent } from './partition-keys'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: KinesisStreamEvent): Array<EventEnvelope> {
  return rawEvents.Records.map(
    (record: KinesisStreamRecord): EventEnvelope => {
      const decodedData = Buffer.from(record.kinesis.data, 'base64').toString()
      return JSON.parse(decodedData) as EventEnvelope
    }
  )
}

export async function storeEvent(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  eventEnvelope: EventEnvelope
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<void> {
  logger.debug('[EventsAdapter#storeEvent] EventEnvelope stored with eventEnvelope:', eventEnvelope)
  await dynamoDB
    .put({
      TableName: config.resourceNames.eventsStore,
      Item: {
        ...eventEnvelope,
        [eventStorePartitionKeyAttributeName]: partitionKeyForEvent(
          eventEnvelope.entityTypeName,
          eventEnvelope.entityID,
          eventEnvelope.kind
        ),
        [eventStoreSortKeyAttributeName]: new Date().toISOString(),
      },
    })
    .promise()
  logger.debug('[EventsAdapter#storeEvent] EventEnvelope stored')
}

export async function readEntityEventsSince(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: string,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime
  const result = await dynamoDB
    .query({
      TableName: config.resourceNames.eventsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${eventStorePartitionKeyAttributeName} = :partitionKey AND ${eventStoreSortKeyAttributeName} > :fromTime`,
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
  entityID: string
): Promise<EventEnvelope | null> {
  const result = await dynamoDB
    .query({
      TableName: config.resourceNames.eventsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${eventStorePartitionKeyAttributeName} = :partitionKey`,
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
