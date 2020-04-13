import { BoosterConfig, Logger, ReadModelRequestEnvelope } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { subscriptionsStorePartitionKeyAttribute, subscriptionsStoreSortKeyAttribute } from '../constants'

export async function subscribeToReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  connectionID: string,
  readModelEnvelope: ReadModelRequestEnvelope
): Promise<void> {
  const { typeName: subscriptionName, ...subscriptionData } = readModelEnvelope
  await db
    .put({
      TableName: config.resourceNames.subscriptionsStore,
      Item: {
        ...subscriptionData,
        [subscriptionsStorePartitionKeyAttribute]: subscriptionName,
        [subscriptionsStoreSortKeyAttribute]: connectionID,
      },
    })
    .promise()
}
