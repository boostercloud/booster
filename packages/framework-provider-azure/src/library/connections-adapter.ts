import { BoosterConfig, ConnectionDataEnvelope } from '@boostercloud/framework-types'
import { connectionsStoreAttributes } from '../constants'
import { CosmosClient } from '@azure/cosmos'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { WebPubSubServiceClient } from '@azure/web-pubsub'

export interface ConnectionIndexRecord extends ConnectionDataEnvelope {
  id: string
  [connectionsStoreAttributes.partitionKey]: string
}

export async function storeConnectionData(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  connectionID: string,
  data: ConnectionDataEnvelope
): Promise<void> {
  const ttl = data[connectionsStoreAttributes.ttl]

  await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.connectionsStore)
    .items.create({
      ...data,
      [connectionsStoreAttributes.partitionKey]: connectionID,
      ttl: ttl,
    })
}

export async function fetchConnectionData(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  connectionID: string
): Promise<ConnectionDataEnvelope | undefined> {
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.connectionsStore)
    .items.query({
      query: `SELECT *
              FROM c
              WHERE c["${connectionsStoreAttributes.partitionKey}"] = @partitionKey`,
      parameters: [
        {
          name: '@partitionKey',
          value: connectionID,
        },
      ],
    })
    .fetchAll()
  return resources[0] as ConnectionDataEnvelope
}

export async function deleteConnectionData(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  connectionID: string
): Promise<void> {
  const logger = getLogger(config, 'connections-adapter#deleteConnectionData')
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.connectionsStore)
    .items.query({
      query: `SELECT *
              FROM c
              WHERE c["${connectionsStoreAttributes.partitionKey}"] = @partitionKey`,
      parameters: [
        {
          name: '@partitionKey',
          value: connectionID,
        },
      ],
    })
    .fetchAll()

  const foundConnections = resources as Array<ConnectionIndexRecord>
  if (foundConnections?.length < 1) {
    logger.info(`No connections found with connectionID=${connectionID}`)
    return
  }

  const connectionsToDelete = foundConnections[0] // There can't be more than one, as we used the full primary key in the query
  logger.debug('Deleting connection = ', connectionsToDelete)

  await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.connectionsStore)
    .item(connectionsToDelete.id, connectionsToDelete[connectionsStoreAttributes.partitionKey])
    .delete()
}

export async function sendMessageToConnection(
  config: BoosterConfig,
  connectionID: string,
  data: unknown
): Promise<void> {
  const logger = getLogger(config, 'connection-adapter#sendMessageToConnection')
  logger.debug(`Sending message ${JSON.stringify(data)} to connection ${connectionID}`)
  const serviceClient = new WebPubSubServiceClient(process.env.WebPubSubConnectionString!, 'booster')
  await serviceClient.sendToConnection(connectionID, JSON.stringify(data), { contentType: 'text/plain' })
}
