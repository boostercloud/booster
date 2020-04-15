import {
  BoosterConfig,
  Logger,
  SubscriptionEnvelope,
  ReadModelInterface,
  ReadModelEnvelope,
} from '@boostercloud/framework-types'
import { ApiGatewayManagementApi, DynamoDB } from 'aws-sdk'
import {
  environmentVarNames,
  subscriptionsStorePartitionKeyAttribute,
  subscriptionsStoreSortKeyAttribute,
  subscriptionsStoreTTLAttribute,
} from '../constants'
import { DynamoDBRecord, DynamoDBStreamEvent } from 'aws-lambda'
import { Converter } from 'aws-sdk/clients/dynamodb'
import { Arn } from './arn'

export async function subscribeToReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> {
  if (
    !subscriptionEnvelope[subscriptionsStorePartitionKeyAttribute] ||
    !subscriptionEnvelope[subscriptionsStoreSortKeyAttribute] ||
    !subscriptionEnvelope[subscriptionsStoreTTLAttribute]
  ) {
    throw new Error(
      'Subscription envelope is missing any of the following required attributers: ' +
        `"${subscriptionsStorePartitionKeyAttribute}", ${subscriptionsStoreSortKeyAttribute}", ${subscriptionsStoreTTLAttribute}"`
    )
  }
  await db
    .put({
      TableName: config.resourceNames.subscriptionsStore,
      Item: subscriptionEnvelope,
    })
    .promise()
}

export async function rawReadModelEventsToEnvelopes(
  config: BoosterConfig,
  logger: Logger,
  rawEvents: DynamoDBStreamEvent
): Promise<Array<ReadModelEnvelope>> {
  return rawEvents.Records.reduce<Array<ReadModelEnvelope>>((readModelEnvelopes, record): Array<ReadModelEnvelope> => {
    return [...readModelEnvelopes, toReadModelEnvelope(config, record)]
  }, [])
}

export async function fetchSubscriptions(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  subscriptionName: string
): Promise<Array<SubscriptionEnvelope>> {
  // TODO: filter expired ones
  const result = await db
    .query({
      TableName: config.resourceNames.subscriptionsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${subscriptionsStorePartitionKeyAttribute} = :partitionKey`,
      ExpressionAttributeValues: {
        ':partitionKey': subscriptionName,
      },
    })
    .promise()

  return result.Items as Array<SubscriptionEnvelope>
}

export async function notifySubscription(
  config: BoosterConfig,
  connectionID: string,
  data: Record<string, any>
): Promise<void> {
  await new ApiGatewayManagementApi({
    endpoint: config.mustGetEnvironmentVar(environmentVarNames.websocketAPIURL),
  })
    .postToConnection({
      ConnectionId: connectionID,
      Data: JSON.stringify(data),
    })
    .promise()
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
