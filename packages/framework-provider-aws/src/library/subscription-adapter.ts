import { BoosterConfig, Logger, SubscriptionEnvelope } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import {
  subscriptionsStorePartitionKeyAttribute,
  subscriptionsStoreSortKeyAttribute,
  subscriptionsStoreTTLAttribute,
} from '../constants'

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
