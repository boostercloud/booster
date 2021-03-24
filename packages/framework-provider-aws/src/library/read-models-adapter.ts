/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-magic-numbers */
import {
  BoosterConfig,
  Logger,
  ReadModelInterface,
  UUID,
  ReadModelEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
} from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { DynamoDBRecord, DynamoDBStreamEvent } from 'aws-lambda'
import { Arn } from './arn'
import { Converter } from 'aws-sdk/clients/dynamodb'

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
  readModelID: UUID
): Promise<ReadModelInterface> {
  const params: DynamoDB.DocumentClient.GetItemInput = {
    TableName: config.resourceNames.forReadModel(readModelName),
    Key: { id: readModelID },
    ConsistentRead: true,
  }
  const response = await db.get(params).promise()
  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
    response.Item
  )
  return response.Item as ReadModelInterface
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
  } catch (e) {
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
  await db
    .delete({
      TableName: config.resourceNames.forReadModel(readModelName),
      Key: { id: readModel.id },
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
