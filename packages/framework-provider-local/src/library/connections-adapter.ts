import { BoosterConfig, ConnectionDataEnvelope } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { WebSocketServerAdapter } from './web-socket-server-adapter'
import { WebSocketRegistry } from '../services/web-socket-registry'

export async function storeConnectionData(
  db: WebSocketRegistry,
  config: BoosterConfig,
  connectionId: string,
  data: ConnectionDataEnvelope
): Promise<void> {
  const logger = getLogger(config, 'connections-adapter#storeConnectionData')
  logger.debug('Storing the following connection envelope:', data)
  await db.store({
    ...data,
    connectionID: connectionId,
  })
}

export async function fetchConnectionData(
  db: WebSocketRegistry,
  config: BoosterConfig,
  connectionId: string
): Promise<ConnectionDataEnvelope | undefined> {
  const results = (await db.query({
    connectionId: connectionId,
  })) as Array<ConnectionDataEnvelope> | undefined
  if (!results || results.length === 0) {
    return
  }
  return results[0] as ConnectionDataEnvelope
}

export async function deleteConnectionData(
  db: WebSocketRegistry,
  config: BoosterConfig,
  connectionId: string
): Promise<void> {
  const logger = getLogger(config, 'connections-adapter#deleteConnectionData')
  const removed = await db.delete({ connectionID: connectionId })
  if (removed === 0) {
    logger.info(`No connections found with connectionID=${connectionId}`)
    return
  }
  logger.debug('Deleted connection = ', connectionId)
}

export async function sendMessageToConnection(
  webSocketServerAdapter: WebSocketServerAdapter,
  config: BoosterConfig,
  connectionId: string,
  data: unknown
): Promise<void> {
  const logger = getLogger(config, 'connection-adapter#sendMessageToConnection')
  logger.debug(`Sending message ${JSON.stringify(data)} to connection ${connectionId}`)
  webSocketServerAdapter.sendToConnection(connectionId, data)
}
