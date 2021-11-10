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
  const sequenceKeyCondition = sequenceKey?.value ? ` AND #${sequenceKey.name} = :${sequenceKey.name}` : ''
  const sequenceAttributeNames = sequenceKey?.value ? { [`#${sequenceKey.name}`]: sequenceKey.name } : {}
  const sequenceAttributeValues = sequenceKey?.value ? { [`:${sequenceKey.name}`]: sequenceKey.value } : {}
  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Performing query for ${readModelName}, with ID ${readModelID} and sequenceKey`,
    sequenceKey
  )
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
    /*
     * TODO: We need to have consistent reads active here to manage the case when we write a new version of the read model
     * and read it in the same lambda execution. For instance, when we're processing the event stream and updating the entities,
     * then read models are updated. We need to make sure that the second time we read it we get the same object we stored in
     * the previous iteration.
     *
     * Still, it would be interesting to make eventual consistent reads for requests coming from the API, as they're faster.
     * One possible solution would be having an extra optional parameter that defaults to `true`, but can be set to `false` in
     * that scenario.
     */
    ConsistentRead: true,
  }
  const response = await db.query(queryParams).promise()
  logger.debug(
    `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
    response.Items
  )

  if (!response.Items || response.Items?.length === 0) {
    return response.Items as unknown as ReadOnlyNonEmptyArray<ReadModelInterface>
  }

  return (response.Items as unknown as ReadOnlyNonEmptyArray<ReadModelInterface>).map((value) => {
    const optimisticConcurrencyValue = generateOptimisticConcurrencyValue(value)
    return {
      ...value,
      boosterMetadata: {
        ...value?.boosterMetadata,
        optimisticConcurrencyValue: optimisticConcurrencyValue,
      },
    } as ReadModelInterface
  }) as unknown as ReadOnlyNonEmptyArray<ReadModelInterface>
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
        ConditionExpression:
          'attribute_not_exists(boosterMetadata.optimisticConcurrencyValue) OR boosterMetadata.optimisticConcurrencyValue = :optimisticConcurrencyValue',
        ExpressionAttributeValues: { ':optimisticConcurrencyValue': expectedCurrentVersion },
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

function generateOptimisticConcurrencyValue(value: ReadModelInterface): number {
  let optimisticConcurrencyValue = 1 // if there is not version then we need to persist the first one
  if (value.boosterMetadata?.version) {
    optimisticConcurrencyValue = value.boosterMetadata?.version + 1 // the next version number that we are going to persist
  }
  return optimisticConcurrencyValue
}
