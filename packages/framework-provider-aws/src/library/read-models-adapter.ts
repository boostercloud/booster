/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-magic-numbers */
import {
  BoosterConfig,
  Logger,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  SequenceKey,
  TimeKey,
  UUID,
} from '@boostercloud/framework-types'
import { DynamoDBRecord, DynamoDBStreamEvent } from 'aws-lambda'
import { DynamoDB } from 'aws-sdk'
import { Converter } from 'aws-sdk/clients/dynamodb'
import { Arn } from './arn'

export async function rawReadModelEventsToEnvelopes(
  config: BoosterConfig,
  logger: Logger,
  rawEvents: DynamoDBStreamEvent
): Promise<Array<ReadModelEnvelope>> {
  return rawEvents.Records.map(toReadModelEnvelope.bind(null, config))
}

export async function fetchReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModelID: UUID,
  sequenceKey?: SequenceKey
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  // We're using query for get single read models too as the difference in performance
  // between get-item and query is none for a single item.
  // Source: https://forums.aws.amazon.com/thread.jspa?threadID=93743
  const sequenceKeyCondition = sequenceKey ? ` AND #${sequenceKey.name} = :${sequenceKey.name}` : ''
  const sequenceAttributeNames = sequenceKey ? { [`#${sequenceKey.name}`]: sequenceKey.name } : {}
  const sequenceAttributeValues = sequenceKey ? { [`:${sequenceKey.name}`]: sequenceKey.value } : {}
  const queryParams: DynamoDB.DocumentClient.QueryInput = {
    TableName: config.resourceNames.forReadModel(readModelName),
    KeyConditionExpression: '#id = :id' + sequenceKeyCondition,
    ExpressionAttributeNames: {
      '#id': 'id',
      ...sequenceAttributeNames,
    },
    ExpressionAttributeValues: {
      ':id': readModelID,
      ...sequenceAttributeValues,
    },
    ConsistentRead: true,
  }
  const response = await db.query(queryParams).promise()
  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
    response.Items
  )
  return response.Items as unknown as ReadOnlyNonEmptyArray<ReadModelInterface>
}

export async function storeReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface,
  expectedCurrentVersion: number
): Promise<void> {
  try {
    await db
      .put({
        TableName: config.resourceNames.forReadModel(readModelName),
        Item: readModel,
        ConditionExpression: 'attribute_not_exists(boosterMetadata.version) OR boosterMetadata.version = :version',
        ExpressionAttributeValues: {
          ':version': expectedCurrentVersion,
        },
      })
      .promise()
  } catch (e: any) {
    // The error will be thrown, but in case of a conditional check, we throw the expected error type by the core
    if (e.name == 'ConditionalCheckFailedException') {
      throw new OptimisticConcurrencyUnexpectedVersionError(e.message)
    }
    throw e
  }
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}

export async function deleteReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  const sequenceKeyName = config.readModelSequenceKeys[readModelName]
  await db
    .delete({
      TableName: config.resourceNames.forReadModel(readModelName),
      Key: buildKey(readModel.id, sequenceKeyName, readModel[sequenceKeyName]),
    })
    .promise()
  logger.debug(`[ReadModelAdapter#deleteReadModel] Read model deleted. ID = ${readModel.id}`)
}

function toReadModelEnvelope(config: BoosterConfig, record: DynamoDBRecord): ReadModelEnvelope {
  if (!record.dynamodb?.NewImage || !record.eventSourceARN) {
    throw new Error('Received a DynamoDB stream event without "eventSourceARN" or "NewImage" field. They are required')
  }
  const tableARNComponents = Arn.parse(record.eventSourceARN)
  if (!tableARNComponents.resourceName) {
    throw new Error('Could not extract the table name from the eventSourceARN')
  }
  const readModelTableName = tableARNComponents.resourceName.split('/')[0]
  return {
    typeName: config.readModelNameFromResourceName(readModelTableName),
    value: Converter.unmarshall(record.dynamodb.NewImage) as ReadModelInterface,
  }
}

function buildKey(
  readModelID: UUID,
  sequenceKeyName?: string,
  sequenceKeyValue?: TimeKey
): DynamoDB.DocumentClient.Key {
  if (sequenceKeyName && sequenceKeyValue) return { id: readModelID, [sequenceKeyName]: sequenceKeyValue }
  else return { id: readModelID }
}
