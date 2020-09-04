import { BoosterConfig, ConnectionDataEnvelope } from '@boostercloud/framework-types'
import { ApiGatewayManagementApi, DynamoDB } from 'aws-sdk'
import { environmentVarNames, connectionsStoreAttributes } from '../constants'

export async function storeConnectionData(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  connectionID: string,
  data: ConnectionDataEnvelope
): Promise<void> {
  await db
    .put({
      TableName: config.resourceNames.connectionsStore,
      Item: {
        ...data,
        [connectionsStoreAttributes.partitionKey]: connectionID,
      },
    })
    .promise()
}

export async function fetchConnectionData(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  connectionID: string
): Promise<ConnectionDataEnvelope | undefined> {
  const response = await db
    .get({
      TableName: config.resourceNames.connectionsStore,
      Key: { [connectionsStoreAttributes.partitionKey]: connectionID },
      ConsistentRead: true,
    })
    .promise()
  return response.Item as ConnectionDataEnvelope
}

export async function deleteConnectionData(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  connectionID: string
): Promise<void> {
  await db
    .delete({
      TableName: config.resourceNames.connectionsStore,
      Key: { [connectionsStoreAttributes.partitionKey]: connectionID },
    })
    .promise()
}

export async function sendMessageToConnection(
  config: BoosterConfig,
  connectionID: string,
  data: unknown
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
