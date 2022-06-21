/* eslint-disable @typescript-eslint/no-unused-vars */
import { DynamoDBRecord, DynamoDBStreamEvent } from 'aws-lambda'
import {
  BoosterConfig,
  EventEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
  UUID,
} from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { eventsStoreAttributes } from '../constants'
import { partitionKeyForEvent, partitionKeyForIndexByEntity } from './keys-helper'
import { Converter } from 'aws-sdk/clients/dynamodb'
import { getLogger, retryIfError } from '@boostercloud/framework-common-helpers'

// eslint-disable-next-line @typescript-eslint/no-magic-numbers
const originOfTime = new Date(0).toISOString()

export function rawEventsToEnvelopes(rawEvents: DynamoDBStreamEvent): Array<EventEnvelope> {
  return rawEvents.Records.map((record: DynamoDBRecord): EventEnvelope => {
    if (!record.dynamodb?.NewImage) {
      throw new Error('Received a DynamoDB stream event without "NewImage" field. It is required')
    }
    return Converter.unmarshall(record.dynamodb?.NewImage) as EventEnvelope
  })
}

export async function readEntityEventsSince(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  entityTypeName: string,
  entityID: UUID,
  since?: string
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'ReadModelStore#readEntityEventsSince')
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
  entityTypeName: string,
  entityID: UUID
): Promise<EventEnvelope | null> {
  const logger = getLogger(config, 'ReadModelStore#readEntityLatestSnapshot')
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
  config: BoosterConfig
): Promise<void> {
  const logger = getLogger(config, 'ReadModelStore#storeEvents')
  logger.debug('[EventsAdapter#storeEvents] Storing the following event envelopes:', eventEnvelopes)
  // const putRequests = []
  for (const eventEnvelope of eventEnvelopes) {
    await retryIfError(() => persistEvent(dynamoDB, config, eventEnvelope), OptimisticConcurrencyUnexpectedVersionError)
  }
  logger.debug('[EventsAdapter#storeEvents] EventEnvelopes stored')
}

async function persistEvent(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  eventEnvelope: EventEnvelope
): Promise<void> {
  try {
    const partitionKey = partitionKeyForEvent(eventEnvelope.entityTypeName, eventEnvelope.entityID, eventEnvelope.kind)
    // Generate a new timestamp as sorting key on every try until we can persist the event.
    // This way we guarantee ordering even with events that are stored in the same millisecond
    const sortKey = new Date().toISOString()
    await dynamoDB
      .put({
        TableName: config.resourceNames.eventsStore,
        ConditionExpression: `${eventsStoreAttributes.partitionKey} <> :partitionKey AND ${eventsStoreAttributes.sortKey} <> :sortKey`,
        ExpressionAttributeValues: {
          ':partitionKey': partitionKey,
          ':sortKey': sortKey,
        },
        Item: {
          ...eventEnvelope,
          [eventsStoreAttributes.partitionKey]: partitionKey,
          [eventsStoreAttributes.sortKey]: sortKey,
          [eventsStoreAttributes.indexByEntity.partitionKey]: partitionKeyForIndexByEntity(
            eventEnvelope.entityTypeName,
            eventEnvelope.kind
          ),
        },
      })
      .promise()
  } catch (e) {
    const error = e as Error
    if (error.name == 'ConditionalCheckFailedException') {
      throw new OptimisticConcurrencyUnexpectedVersionError(error.message)
    }
    throw e
  }
}
