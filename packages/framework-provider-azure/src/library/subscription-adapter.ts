import { BoosterConfig, SubscriptionEnvelope } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { subscriptionsStoreAttributes } from '../constants'
import { getLogger } from '@boostercloud/framework-common-helpers'

export interface SubscriptionIndexRecord {
  id: string
  [subscriptionsStoreAttributes.partitionKey]: string
  [subscriptionsStoreAttributes.sortKey]: string
  [subscriptionsStoreAttributes.indexByConnectionIDPartitionKey]: string
  [subscriptionsStoreAttributes.indexByConnectionIDSortKey]: string
}

export async function subscribeToReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
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

  const ttl = subscriptionEnvelope[subscriptionsStoreAttributes.ttl]

  await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.subscriptionsStore)
    .items.create({
      ...subscriptionEnvelope,
      [subscriptionsStoreAttributes.sortKey]: sortKeyForSubscription(
        subscriptionEnvelope.connectionID,
        subscriptionEnvelope.operation.id
      ),
      [subscriptionsStoreAttributes.indexByConnectionIDSortKey]: subscriptionEnvelope.operation.id,
      ttl: ttl,
    })
}

export async function fetchSubscriptions(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  subscriptionName: string
): Promise<Array<SubscriptionEnvelope>> {
  // TODO: filter expired ones. Or... is it needed?
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.subscriptionsStore)
    .items.query({
      query: `SELECT *
              FROM c
              WHERE c["${subscriptionsStoreAttributes.partitionKey}"] = @partitionKey`,
      parameters: [{ name: '@partitionKey', value: subscriptionName }],
    })
    .fetchAll()

  return resources
}

export async function deleteSubscription(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  connectionID: string,
  subscriptionID: string
): Promise<void> {
  const logger = getLogger(config, 'subscription-adapter#deleteSubscription')
  // TODO: Manage query pagination
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.subscriptionsStore)
    .items.query({
      query: `SELECT *
              FROM c
              WHERE c["${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey}"] = @partitionKey
                AND c["${subscriptionsStoreAttributes.indexByConnectionIDSortKey}"] = @sortKey`,
      parameters: [
        {
          name: '@partitionKey',
          value: connectionID,
        },
        {
          name: '@sortKey',
          value: subscriptionID,
        },
      ],
    })
    .fetchAll()

  const foundSubscriptions = resources as Array<SubscriptionIndexRecord>
  if (foundSubscriptions?.length < 1) {
    logger.info(
      `[deleteSubscription] No subscriptions found with connectionID=${connectionID} and subscriptionID=${subscriptionID}`
    )
    return
  }

  const subscriptionToDelete = foundSubscriptions[0] // There can't be more than one, as we used the full primary key in the query
  logger.debug('[deleteSubscription] Deleting subscription = ', subscriptionToDelete)

  await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.subscriptionsStore)
    .item(subscriptionToDelete.id, subscriptionToDelete[subscriptionsStoreAttributes.partitionKey])
    .delete()
}

export async function deleteAllSubscriptions(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  connectionID: string
): Promise<void> {
  const logger = getLogger(config, 'subscription-adapter#deleteAllSubscriptions')
  // TODO: Manage query pagination and db.batchWrite limit of 25 operations at a time
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.subscriptionsStore)
    .items.query({
      query: `SELECT *
              FROM c
              WHERE c["${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey}"] = @partitionKey`,
      parameters: [
        {
          name: '@partitionKey',
          value: connectionID,
        },
      ],
    })
    .fetchAll()

  const foundSubscriptions = resources as Array<SubscriptionIndexRecord>
  if (foundSubscriptions?.length < 1) {
    logger.info(`No subscriptions found with connectionID=${connectionID}`)
    return
  }

  logger.debug(`Deleting all subscriptions for connectionID=${connectionID}, which are: `, foundSubscriptions)

  const deletePromises = foundSubscriptions.map((subscriptionRecord) =>
    cosmosDb
      .database(config.resourceNames.applicationStack)
      .container(config.resourceNames.subscriptionsStore)
      .item(subscriptionRecord.id, subscriptionRecord[subscriptionsStoreAttributes.partitionKey])
      .delete()
  )
  await Promise.allSettled(deletePromises)
}

function sortKeyForSubscription(connectionID: string, subscriptionID: string): string {
  return `${connectionID}-${subscriptionID}`
}
