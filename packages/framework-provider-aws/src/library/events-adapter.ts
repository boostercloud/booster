import { KinesisStreamEvent, KinesisStreamRecord } from 'aws-lambda'
import { BoosterConfig, EventEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import { DynamoDB, Kinesis } from 'aws-sdk'
import { eventStorePartitionKeyAttribute, eventStoreSortKeyAttribute } from '../constants'
import { partitionKeyForEvent } from './partition-keys'
import { PutRecordsRequestEntry } from 'aws-sdk/clients/kinesis'

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
        [eventStorePartitionKeyAttribute]: partitionKeyForEvent(
          eventEnvelope.entityTypeName,
          eventEnvelope.entityID,
          eventEnvelope.kind
        ),
        [eventStoreSortKeyAttribute]: new Date().toISOString(),
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
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const fromTime = since ? since : originOfTime
  const result = await dynamoDB
    .query({
      TableName: config.resourceNames.eventsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${eventStorePartitionKeyAttribute} = :partitionKey AND ${eventStoreSortKeyAttribute} > :fromTime`,
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
      KeyConditionExpression: `${eventStorePartitionKeyAttribute} = :partitionKey`,
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

export async function publishEvents(
  eventsStream: Kinesis,
  eventEnvelopes: Array<EventEnvelope>,
  config: BoosterConfig,
  logger: Logger
): Promise<void> {
  logger.info('Publishing the following events:', eventEnvelopes)
  const publishResult = await eventsStream
    .putRecords({
      StreamName: config.resourceNames.eventsStream,
      Records: eventEnvelopes.map(toPutRecordsEntity),
    })
    .promise()
  logger.debug('Events published with result', publishResult.$response)
}

function toPutRecordsEntity(eventEnvelope: EventEnvelope): PutRecordsRequestEntry {
  return {
    PartitionKey: partitionKeyForEvent(eventEnvelope.entityTypeName, eventEnvelope.entityID),
    Data: Buffer.from(JSON.stringify(eventEnvelope)),
  }
}
