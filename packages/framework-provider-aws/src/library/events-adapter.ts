import { DynamoDBStreamEvent, DynamoDBRecord } from 'aws-lambda'
import { BoosterConfig, EventEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { eventsStoreAttributes } from '../constants'
import { partitionKeyForEvent } from './partition-keys'
import { Converter, DocumentClient } from 'aws-sdk/clients/dynamodb'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: DynamoDBStreamEvent): Array<EventEnvelope> {
  return rawEvents.Records.filter((item: DynamoDBRecord) => item.dynamodb?.NewImage).map(
    (record: DynamoDBRecord): EventEnvelope => {
      // Here we are really sure that we've got only new records to emit
      return Converter.unmarshall(record.dynamodb?.NewImage!) as EventEnvelope
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
  // TODO: Manage query pagination and db.batchWrite limit of 25 operations at a time
  logger.debug('[EventsAdapter#storeEvents] Storing EventEnvelopes with eventEnvelopes:', eventEnvelopes)
  const params: DynamoDB.DocumentClient.BatchWriteItemInput = {
    RequestItems: {
      [config.resourceNames.eventsStore]: eventEnvelopes.map((eventEnvelope) => ({
        PutRequest: {
          Item: {
            ...eventEnvelope,
            [eventsStoreAttributes.partitionKey]: partitionKeyForEvent(
              eventEnvelope.entityTypeName,
              eventEnvelope.entityID,
              eventEnvelope.kind
            ),
            [eventsStoreAttributes.sortKey]: new Date().toISOString(),
          },
        },
      })),
    },
  }
  await dynamoDB.batchWrite(params).promise()
  logger.debug('[EventsAdapter#storeEvents] EventEnvelope stored')
}

export async function destroyEntity(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  entityID: UUID
): Promise<void> {
  logger.debug('[EventsAdapter#destroyEntity] Destroying entity')
  // BatchWrite limitation, in the future we should move this value to BoosterConfig. We are assuming that this process will be
  // fit into lambda memory and time execution limits.
  const MAX_EVENTS_TO_DELETE = 25
  let nextKey: DocumentClient.Key | undefined = undefined
  let result = null
  do {
    result = await dynamoDB
      .scan({
        TableName: config.resourceNames.eventsStore,
        ConsistentRead: true,
        FilterExpression: 'entityTypeName = :entityTypeName AND entityID = :entityID',
        ExpressionAttributeValues: {
          ':entityTypeName': entityTypeName,
          ':entityID': entityID,
        },
        Limit: MAX_EVENTS_TO_DELETE,
        ExclusiveStartKey: nextKey,
      })
      .promise()

    if (result?.Items) {
      const events = result?.Items as Array<EventEnvelope>

      const params: DynamoDB.DocumentClient.BatchWriteItemInput = {
        RequestItems: {
          [config.resourceNames.eventsStore]: events.map((eventEnvelope) => ({
            DeleteRequest: {
              Key: {
                [eventsStoreAttributes.partitionKey]: partitionKeyForEvent(
                  eventEnvelope.entityTypeName,
                  eventEnvelope.entityID,
                  eventEnvelope.kind
                ),
                [eventsStoreAttributes.sortKey]: eventEnvelope.createdAt,
              },
            },
          })),
        },
      }
      await dynamoDB.batchWrite(params).promise()
    }
    nextKey = result?.LastEvaluatedKey
  } while (nextKey)
  logger.debug('[EventsAdapter#destroyEntity] Entity destroyed')
}
