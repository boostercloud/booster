import { BoosterConfig, ConnectionDataEnvelope } from '@boostercloud/framework-types'
import { ApiGatewayManagementApi, DynamoDB } from 'aws-sdk'
import { environmentVarNames } from '../constants'

export async function storeConnectionData(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  connectionID: string,
  data: Record<string, any>
): Promise<void> {}

export async function fetchConnectionData(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  connectionID: string
): Promise<ConnectionDataEnvelope> {
  return undefined as any
}

export async function deleteConnectionData(
  db: DynamoDB.DocumentClient,
  config: BoosterConfig,
  connectionID: string
): Promise<void> {}

export async function sendMessageToConnection(
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
