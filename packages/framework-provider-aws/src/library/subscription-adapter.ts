import { BoosterConfig, Logger, SubscriptionEnvelope, ReadModelInterface } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import {
  subscriptionsStorePartitionKeyAttribute,
  subscriptionsStoreSortKeyAttribute,
  subscriptionsStoreTTLAttribute,
} from '../constants'
import { DynamoDBStreamEvent } from 'aws-lambda'
import { Converter } from 'aws-sdk/clients/dynamodb'

export async function subscribeToReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  connectionID: string,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> {
  const { typeName: subscriptionName, expirationTimeEpoch: expirationTime, ...subscriptionData } = subscriptionEnvelope
  await db
    .put({
      TableName: config.resourceNames.subscriptionsStore,
      Item: {
        ...subscriptionData,
        [subscriptionsStorePartitionKeyAttribute]: subscriptionName,
        [subscriptionsStoreSortKeyAttribute]: connectionID,
        [subscriptionsStoreTTLAttribute]: expirationTime,
      },
    })
    .promise()
}
export async function rawReadModelEventsToEnvelope(rawEvents: DynamoDBStreamEvent): Promise<Array<ReadModelInterface>> {
  return rawEvents.Records.reduce<Array<ReadModelInterface>>((readModels, record): Array<ReadModelInterface> => {
    if (!record.dynamodb?.NewImage) {
      return readModels
    }
    return [...readModels, Converter.unmarshall(record.dynamodb.NewImage) as ReadModelInterface]
  }, [])
}
