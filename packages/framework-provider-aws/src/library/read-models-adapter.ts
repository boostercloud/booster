/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-magic-numbers */
import {
  BoosterConfig,
  Logger,
  ReadModelInterface,
  UUID,
  ReadModelEnvelope,
  OptimisticConcurrencyUnexpectedVersionError,
  TimeKey,
  SequenceKey,
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
  readModelID: UUID,
  sequenceKey?: SequenceKey
): Promise<ReadModelInterface> {
  const params: DynamoDB.DocumentClient.GetItemInput = {
    TableName: config.resourceNames.forReadModel(readModelName),
    Key: buildKey(readModelID, sequenceKey?.name, sequenceKey?.value),
    /*
     * TODO: We need to have consistent reads active here to manage the case when we write a new version of the read model
     * and read it in the same lambda execution. For instance, when we're processing the event stream and updating the entities,
     * then read models are updated. We need to make sure that the second time we read it we get the same object we stored in
     * the previous iteration.
     *
     * Still, it would be interesting to make eventual consistent reads for requests coming from the API, because they're faster.
     * One possible solution would be having an extra optional parameter that defaults to `true`, but can be set to `false` in
     * that scenario.
     */
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
