import { Server, WebSocket } from 'ws'
import { Server as HttpServer } from 'http'
import { Duplex } from 'stream'
import { BoosterConfig, EventType, UUID } from '@boostercloud/framework-types'
import { IncomingMessage } from 'node:http'
import { GraphQLService } from '../services'
import * as express from 'express'
import { getLogger } from '@boostercloud/framework-common-helpers'

export interface ConnectionContext {
  connectionId: UUID
  eventType: EventType
}

export interface ExpressWebSocketMessage {
  connectionContext: ConnectionContext
  incomingMessage?: IncomingMessage
  data?: unknown
}

const LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT = 'LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT'

export class WebSocketServerAdapter {
  private readonly httpServer: HttpServer
  private readonly webSocketServer: Server
  private readonly clients: Record<string, WebSocket> = {}
  private readonly WS_PORT = process.env[LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT] ?? '65529'

  constructor(readonly graphQLService: GraphQLService, config: BoosterConfig) {
    const expressServer = express()
    this.webSocketServer = new WebSocket.Server({
      noServer: true,
      path: '/websocket',
    })
    this.httpServer = expressServer.listen(this.WS_PORT, () => {})
    const logger = getLogger(config, 'WebSocketServerAdapter#constructor#handleUpgrade')
    logger.info(`Starting websocket server on ws://localhost:${this.WS_PORT}/websocket`)
    this.attach()
    this.onConnection(config)
  }

  public attach(): void {
    this.httpServer.on('upgrade', (request: IncomingMessage, socket: Duplex, head: Buffer) => {
      this.webSocketServer.handleUpgrade(request, socket, head, (webSocket: WebSocket) => {
        this.webSocketServer.emit('connection', webSocket, request)
      })
    })
  }

  private onConnection(config: BoosterConfig): void {
    this.webSocketServer.on('connection', async (webSocket: WebSocket, connectionRequest: IncomingMessage) => {
      const connectionId = UUID.generate()
      this.clients[connectionId.toString()] = webSocket
      const request: ExpressWebSocketMessage = {
        incomingMessage: connectionRequest,
        connectionContext: {
          connectionId: connectionId,
          eventType: 'CONNECT',
        },
      }
      await this.graphQLService.handleGraphQLRequest(request)
      this.onMessage(config, webSocket, connectionRequest, request.connectionContext.connectionId)
      this.onDisconnect(webSocket, request.connectionContext.connectionId)
    })
  }

  private onMessage(
    config: BoosterConfig,
    webSocket: WebSocket,
    connectionRequest: IncomingMessage,
    connectionId: UUID
  ) {
    const logger = getLogger(config, 'web-socket-server-adapter#onMessage')
    webSocket.on('message', async (message) => {
      let data: unknown
      try {
        data = JSON.parse(message.toString())
      } catch (e) {
        logger.error('Error parsing websocket message', e)
        return
      }
      const request: ExpressWebSocketMessage = {
        data: data,
        incomingMessage: connectionRequest,
        connectionContext: {
          connectionId: connectionId,
          eventType: 'MESSAGE',
        },
      }
      await this.graphQLService.handleGraphQLRequest(request)
    })
  }

  private onDisconnect(webSocket: WebSocket, connectionId: UUID): void {
    webSocket.on('close', async () => {
      const request: ExpressWebSocketMessage = {
        connectionContext: {
          connectionId: connectionId,
          eventType: 'DISCONNECT',
        },
      }
      await this.graphQLService.handleGraphQLRequest(request)
    })
  }

  public sendToConnection(connectionId: string, data: unknown): void {
    const stringData = JSON.stringify(data)
    this.clients[connectionId].send(stringData)
  }
}
