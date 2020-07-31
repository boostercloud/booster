import { BoosterConfig, Logger, SubscriptionEnvelope } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { subscriptionsStoreAttributes } from '../constants'
import { sortKeyForSubscription } from './partition-keys'

export interface SubscriptionIndexRecord {
  [subscriptionsStoreAttributes.partitionKey]: string
  [subscriptionsStoreAttributes.sortKey]: string
  [subscriptionsStoreAttributes.indexByConnectionIDPartitionKey]: string
  [subscriptionsStoreAttributes.indexByConnectionIDSortKey]: string
}

export async function subscribeToReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> {
  if (
    !subscriptionEnvelope[subscriptionsStoreAttributes.partitionKey] ||
    !subscriptionEnvelope.connectionID || // First part of subscriptionsStoreAttributes.sortKey
    !subscriptionEnvelope.operation.id || // Second part of subscriptionsStoreAttributes.sortKey
    !subscriptionEnvelope[subscriptionsStoreAttributes.ttl]
  ) {
    throw new Error(
      'Subscription envelope is missing any of the following required attributes: ' +
        `"${subscriptionsStoreAttributes.partitionKey}", "connectionID", "operation.id", ${subscriptionsStoreAttributes.ttl}"`
    )
  }

  await db
    .put({
      TableName: config.resourceNames.subscriptionsStore,
      Item: {
        ...subscriptionEnvelope,
        [subscriptionsStoreAttributes.sortKey]: sortKeyForSubscription(
          subscriptionEnvelope.connectionID,
          subscriptionEnvelope.operation.id
        ),
        [subscriptionsStoreAttributes.indexByConnectionIDSortKey]: subscriptionEnvelope.operation.id,
      },
    })
    .promise()
}

export async function fetchSubscriptions(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  subscriptionName: string
): Promise<Array<SubscriptionEnvelope>> {
  // TODO: filter expired ones. Or... is it needed?
  const result = await db
    .query({
      TableName: config.resourceNames.subscriptionsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${subscriptionsStoreAttributes.partitionKey} = :partitionKey`,
      ExpressionAttributeValues: {
        ':partitionKey': subscriptionName,
      },
    })
    .promise()

  return result.Items as Array<SubscriptionEnvelope>
}

export async function deleteSubscription(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  connectionID: string,
  subscriptionID: string
): Promise<void> {
  // TODO: Manage query pagination
  const result = await db
    .query({
      TableName: config.resourceNames.subscriptionsStore,
      IndexName: subscriptionsStoreAttributes.indexByConnectionIDName(config),
      KeyConditionExpression:
        `${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey AND ` +
        `${subscriptionsStoreAttributes.indexByConnectionIDSortKey} = :sortKey`,
      ExpressionAttributeValues: {
        ':partitionKey': connectionID,
        ':sortKey': subscriptionID,
      },
    })
    .promise()

  const foundSubscriptions = result.Items as Array<SubscriptionIndexRecord>
  if (foundSubscriptions?.length < 1) {
    logger.info(
      `[deleteSubscription] No subscriptions found with connectionID=${connectionID} and subscriptionID=${subscriptionID}`
    )
    return
  }

  const subscriptionToDelete = foundSubscriptions[0] // There can't be more than one, as we used the full primary key in the query
  logger.debug('[deleteSubscription] Deleting subscription = ', subscriptionToDelete)
  await db
    .delete({
      TableName: config.resourceNames.subscriptionsStore,
      Key: {
        [subscriptionsStoreAttributes.partitionKey]: subscriptionToDelete[subscriptionsStoreAttributes.partitionKey],
        [subscriptionsStoreAttributes.sortKey]: subscriptionToDelete[subscriptionsStoreAttributes.sortKey],
      },
    })
    .promise()
}

export async function deleteAllSubscriptions(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  connectionID: string
): Promise<void> {
  // TODO: Manage query pagination and db.batchWrite limit of 25 operations at a time
  const result = await db
    .query({
      TableName: config.resourceNames.subscriptionsStore,
      IndexName: subscriptionsStoreAttributes.indexByConnectionIDName(config),
      KeyConditionExpression: `${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey`,
      ExpressionAttributeValues: { ':partitionKey': connectionID },
    })
    .promise()
  const foundSubscriptions = result.Items as Array<SubscriptionIndexRecord>
  if (foundSubscriptions?.length < 1) {
    logger.info(`[deleteAllSubscription] No subscriptions found with connectionID=${connectionID}`)
    return
  }

  logger.debug(
    `[deleteAllSubscription] Deleting all subscriptions for connectionID=${connectionID}, which are: `,
    foundSubscriptions
  )

  const params: DynamoDB.DocumentClient.BatchWriteItemInput = {
    RequestItems: {
      [config.resourceNames.subscriptionsStore]: foundSubscriptions.map((subscriptionRecord) => ({
        DeleteRequest: {
          Key: {
            [subscriptionsStoreAttributes.partitionKey]:
              subscriptionRecord[subscriptionsStoreAttributes.partitionKey],
            [subscriptionsStoreAttributes.sortKey]: subscriptionRecord[subscriptionsStoreAttributes.sortKey],
          },
        },
      })),
    },
  }

  await db.batchWrite(params).promise()
}
