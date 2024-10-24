import {
  BoosterConfig,
  EventEnvelope,
  EventInterface,
  EventSearchParameters,
  EventSearchResponse,
  PaginatedEntitiesIdsResult,
  UUID,
} from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { dynamoDbBatchGetLimit, eventsStoreAttributes } from '../constants'
import { DocumentClient } from 'aws-sdk/clients/dynamodb'
import { partitionKeyForEvent, partitionKeyForIndexByEntity } from './keys-helper'
import { inChunksOf } from '../pagination-helpers'
import { getLogger } from '@boostercloud/framework-common-helpers'

export async function searchEvents(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  parameters: EventSearchParameters
): Promise<Array<EventSearchResponse>> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEvents')
  logger.debug('Initiating an events search. Filters: ', parameters)
  const timeFilterQuery = buildSearchEventsTimeQuery(parameters.from, parameters.to)
  const eventEnvelopes = await executeSearch(dynamoDB, config, parameters, timeFilterQuery, parameters.limit)

  logger.debug('Events search result: ', eventEnvelopes)
  return convertToSearchResult(eventEnvelopes)
}

export async function searchEntitiesIds(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  limit: number,
  afterCursor: Record<string, string> | undefined,
  entityTypeName: string
): Promise<PaginatedEntitiesIdsResult> {
  throw new Error('eventsSearcherAdapter#searchEntitiesIds: Not implemented yet')
}

interface TimeQueryData {
  expression: string
  attributeValues: Record<string, string>
}

function buildSearchEventsTimeQuery(from?: string, to?: string): TimeQueryData {
  let timeQueryData: TimeQueryData = {
    expression: '',
    attributeValues: {},
  }
  if (from && to) {
    timeQueryData = {
      expression: ` AND ${eventsStoreAttributes.sortKey} BETWEEN :fromTime AND :toTime`,
      attributeValues: {
        ':fromTime': from,
        ':toTime': to,
      },
    }
  } else if (from) {
    timeQueryData = {
      expression: ` AND ${eventsStoreAttributes.sortKey} >= :fromTime`,
      attributeValues: { ':fromTime': from },
    }
  } else if (to) {
    timeQueryData = {
      expression: ` AND ${eventsStoreAttributes.sortKey} <= :toTime`,
      attributeValues: { ':toTime': to },
    }
  }
  return timeQueryData
}

async function executeSearch(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  filters: EventSearchParameters,
  timeFilterQuery: TimeQueryData,
  limit?: number
): Promise<Array<EventEnvelope>> {
  if ('entity' in filters) {
    if (filters.entityID) {
      return await searchEventsByEntityAndID(dynamoDB, config, filters.entity, filters.entityID, timeFilterQuery, limit)
    } else {
      return await searchEventsByEntity(dynamoDB, config, filters.entity, timeFilterQuery, limit)
    }
  } else if ('type' in filters) {
    return await searchEventsByType(dynamoDB, config, filters.type, timeFilterQuery, limit)
  } else {
    throw new Error('Invalid search event query. It is neither an search by "entity" nor a search by "type"')
  }
}

async function searchEventsByEntityAndID(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  entity: string,
  entityID: UUID,
  timeQuery: TimeQueryData,
  limit?: number
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEventsByEntityAndID')
  // TODO: Manage pagination
  const params: DocumentClient.QueryInput = {
    TableName: config.resourceNames.eventsStore,
    ConsistentRead: true,
    ScanIndexForward: false, // Descending order (newer timestamps first)
    Limit: limit,
    KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey${timeQuery.expression}`,
    ExpressionAttributeValues: {
      ...timeQuery.attributeValues,
      ':partitionKey': partitionKeyForEvent(entity, entityID),
    },
  }

  logger.debug('Searching events by entity and entity ID. Query params: ', params)
  const result = await dynamoDB.query(params).promise()
  return (result.Items as Array<EventEnvelope>) ?? []
}

interface EventStoreKeys {
  [eventsStoreAttributes.partitionKey]: string
  [eventsStoreAttributes.sortKey]: string
}

async function searchEventsByEntity(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  entity: string,
  timeQuery: TimeQueryData,
  limit?: number
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEventsByEntity')
  // TODO: manage pagination
  // First query the index
  const params: DocumentClient.QueryInput = {
    TableName: config.resourceNames.eventsStore,
    IndexName: eventsStoreAttributes.indexByEntity.name(config),
    ScanIndexForward: false, // Descending order (newer timestamps first)
    Limit: limit,
    KeyConditionExpression: `${eventsStoreAttributes.indexByEntity.partitionKey} = :partitionKey${timeQuery.expression}`,
    ExpressionAttributeValues: {
      ...timeQuery.attributeValues,
      ':partitionKey': partitionKeyForIndexByEntity(entity, 'event'),
    },
  }

  logger.debug('Searching events by entity. Index query params: ', params)
  const partialResult = await dynamoDB.query(params).promise()
  const indexRecords = (partialResult.Items as Array<EventStoreKeys>) ?? []
  return findEventsDataWithKeys(dynamoDB, config, indexRecords)
}

async function searchEventsByType(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  type: string,
  timeQuery: TimeQueryData,
  limit?: number
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEventsByType')
  // TODO: manage pagination
  // Fist query the index
  const params: DocumentClient.QueryInput = {
    TableName: config.resourceNames.eventsStore,
    IndexName: eventsStoreAttributes.indexByType.name(config),
    ScanIndexForward: false, // Descending order (newer timestamps first)
    Limit: limit,
    KeyConditionExpression: `${eventsStoreAttributes.indexByType.partitionKey} = :partitionKey${timeQuery.expression}`,
    ExpressionAttributeValues: {
      ...timeQuery.attributeValues,
      ':partitionKey': type,
    },
  }

  logger.debug('Searching events by type. Index query params: ', params)
  const partialResult = await dynamoDB.query(params).promise()
  const indexRecords = (partialResult.Items as Array<EventStoreKeys>) ?? []
  return findEventsDataWithKeys(dynamoDB, config, indexRecords)
}

async function findEventsDataWithKeys(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  keys: Array<EventStoreKeys>
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-searcher-adapter#findEventsDataWithKeys')
  const result: Array<EventEnvelope> = []

  const keysBatches = inChunksOf(dynamoDbBatchGetLimit, keys)
  logger.debug(`Performing batch get for ${keysBatches.length} batches`)
  for (const keysBatch of keysBatches) {
    const batchResult = await performBatchGet(dynamoDB, config, keysBatch)
    result.push(...batchResult)
  }

  return result
}

async function performBatchGet(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  keys: Array<EventStoreKeys>
): Promise<Array<EventEnvelope>> {
  const logger = getLogger(config, 'events-searcher-adapter#performBatchGet')
  const params: DocumentClient.BatchGetItemInput = {
    RequestItems: {
      [config.resourceNames.eventsStore]: {
        ConsistentRead: true,
        Keys: keys.map((record) => {
          return {
            [eventsStoreAttributes.partitionKey]: record[eventsStoreAttributes.partitionKey],
            [eventsStoreAttributes.sortKey]: record[eventsStoreAttributes.sortKey],
          }
        }),
      },
    },
  }

  logger.debug('Finding events data for keys: ', keys, params)
  const result = await dynamoDB.batchGet(params).promise()
  return (result.Responses?.[config.resourceNames.eventsStore] as Array<EventEnvelope>) ?? []
}

function convertToSearchResult(eventEnvelopes: Array<EventEnvelope>): Array<EventSearchResponse> {
  // "eventEnvelopes" represent a result page. That page is absolutely ordered among all the items in the
  // database. BUT! the local order within the page is been messed up, so we need to sort the items.
  // Why the order is messed up? Because we are doing two queries to DynamoDB:
  // - One to the corresponding table index to know the keys of the items we need to retrieve from the master table.
  // The result of this query is paginated and the absolute order of items is respected.
  // - Another one to the master table to get the items data. This query is made with "batchQueryItems", which
  // does not preserve the order in which we specify the keys. This is why we need to sort the final result.
  // It affects also to the limit that could only be applied to the ordered elements
  return eventEnvelopes
    .map((eventEnvelope) => {
      return {
        type: eventEnvelope.typeName,
        entity: eventEnvelope.entityTypeName,
        entityID: eventEnvelope.entityID,
        requestID: eventEnvelope.requestID,
        user: eventEnvelope.currentUser,
        createdAt: eventEnvelope.createdAt,
        value: eventEnvelope.value as EventInterface,
        deletedAt: eventEnvelope.deletedAt,
      }
    })
    .sort((a, b) => {
      if (a.createdAt > b.createdAt) return -1
      if (a.createdAt < b.createdAt) return 1
      return 0
    })
}
