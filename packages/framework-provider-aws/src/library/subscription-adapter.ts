import { BoosterConfig, Logger, SubscriptionEnvelope } from '@boostercloud/framework-types'
import { ApiGatewayManagementApi, DynamoDB } from 'aws-sdk'
import { environmentVarNames, subscriptionsStoreAttributes } from '../constants'

export async function subscribeToReadModel(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> {
  if (
    !subscriptionEnvelope[subscriptionsStoreAttributes.partitionKey] ||
    !subscriptionEnvelope[subscriptionsStoreAttributes.sortKey] ||
    !subscriptionEnvelope[subscriptionsStoreAttributes.ttl] ||
    !subscriptionEnvelope.operation.id
  ) {
    throw new Error(
      'Subscription envelope is missing any of the following required attributes: ' +
        `"${subscriptionsStoreAttributes.partitionKey}", ${subscriptionsStoreAttributes.sortKey}", ${subscriptionsStoreAttributes.ttl}", ` +
        '"subscriptionEnvelope.operation.id"'
    )
  }

  await db
    .put({
      TableName: config.resourceNames.subscriptionsStore,
      Item: {
        ...subscriptionEnvelope,
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
      ConsistentRead: true,
      KeyConditionExpression:
        `${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey AND ` +
        `${subscriptionsStoreAttributes.indexByConnectionIDSortKey} = :sortKey`,
      ExpressionAttributeValues: {
        ':partitionKey': connectionID,
        ':sortKey': subscriptionID,
      },
    })
    .promise()
  const foundSubscriptions = result.Items as Array<SubscriptionEnvelope>
  if (foundSubscriptions?.length == 0) {
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
        [subscriptionsStoreAttributes.partitionKey]: subscriptionToDelete.typeName,
        [subscriptionsStoreAttributes.sortKey]: subscriptionToDelete.connectionID,
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
      ConsistentRead: true,
      KeyConditionExpression: `${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey`,
      ExpressionAttributeValues: { ':partitionKey': connectionID },
    })
    .promise()
  const foundSubscriptions = result.Items as Array<SubscriptionEnvelope>
  if (foundSubscriptions?.length == 0) {
    logger.info(`[deleteAllSubscription] No subscriptions found with connectionID=${connectionID}`)
    return
  }

  logger.debug(
    `[deleteAllSubscription] Deleting all subscriptions for connectionID=${connectionID}, which are: `,
    foundSubscriptions
  )

  const params: DynamoDB.DocumentClient.BatchWriteItemInput = {
    RequestItems: {
      [config.resourceNames.subscriptionsStore]: foundSubscriptions.map((subscriptionEnvelope) => ({
        DeleteRequest: {
          Key: {
            [subscriptionsStoreAttributes.partitionKey]: subscriptionEnvelope.typeName,
            [subscriptionsStoreAttributes.sortKey]: subscriptionEnvelope.connectionID,
          },
        },
      })),
    },
  }

  await db.batchWrite(params).promise()
}
