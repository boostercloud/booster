import { ExecutionResult } from 'graphql'
import {
  GraphQLRequestEnvelope,
  Logger,
  MessageTypes,
  GraphQLClientMessage,
  GraphQLInitError,
  GraphQLServerMessage,
  GraphQLInitAck,
  GraphQLKeepAlive,
  GraphQLData,
  GraphQLStart,
  GraphQLComplete,
} from '@boostercloud/framework-types'

export class GraphQLWebsocketHandler {
  public constructor(
    private readonly logger: Logger,
    private readonly messageSender: (connectionID: string, data: GraphQLServerMessage) => Promise<void>,
    private readonly onOperation: (
      envelope: GraphQLRequestEnvelope
    ) => Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult>,
    private readonly onUnsubscribe: (envelope: GraphQLRequestEnvelope) => Promise<ExecutionResult>,
    private readonly onTerminate: (envelope: GraphQLRequestEnvelope) => Promise<ExecutionResult>
  ) {}

  public async handle(envelope: GraphQLRequestEnvelope): Promise<GraphQLServerMessage> {
    try {
      this.logger.debug('Handling websocket message')
      if (!envelope.value) {
        throw new Error('Received an empty GraphQL body')
      }
      if (!envelope.connectionID) {
        throw new Error('Missing websocket connectionID')
      }
      const clientMessage = envelope.value as GraphQLClientMessage
      this.logger.debug('Received client message: ', clientMessage)
      switch (clientMessage.type) {
        case MessageTypes.GQL_CONNECTION_INIT:
          return this.handleInit(envelope.connectionID)
        case MessageTypes.GQL_START:
          return this.handleStart(envelope.connectionID, envelope, clientMessage)
        case MessageTypes.GQL_STOP:
          console.log(this.onUnsubscribe) // TODO
          break
        case MessageTypes.GQL_CONNECTION_TERMINATE:
          console.log(this.onTerminate) // TODO
          break
        default:
          // This branch should be impossible, but just in case
          return new GraphQLInitError(`Unknown message type. Message=${clientMessage}`)
      }
    } catch (e) {
      this.logger.error(e)
      return new GraphQLInitError(e.message)
    }
    return undefined as any // TODO remove this when all switch cases are handled
  }

  private async handleInit(connectionID: string): Promise<GraphQLServerMessage> {
    this.logger.debug('Sending ACK')
    await this.messageSender(connectionID, new GraphQLInitAck())
    this.logger.debug('Sending KEEP_ALIVE')
    return new GraphQLKeepAlive()
  }

  private async handleStart(
    connectionID: string,
    envelope: GraphQLRequestEnvelope,
    message: GraphQLStart
  ): Promise<GraphQLServerMessage> {
    if (!message.id) {
      throw new Error(`Missing "id" in ${message.type} message`)
    }
    if (!message.payload || !message.payload.query) {
      throw new Error('Message payload is invalid it must contain at least the "query" property')
    }
    const unwrappedEnvelope: GraphQLRequestEnvelope = {
      ...envelope,
      value: {
        ...message.payload,
        id: message.id,
      },
    }

    this.logger.debug('Executing operation. Envelope: ', unwrappedEnvelope)
    const result = await this.onOperation(unwrappedEnvelope)

    this.logger.debug('Execution finished. Sending DATA:', result)
    if ('next' in result) {
      // It was a subscription. We send data and nothing more
      return new GraphQLData(message.id)
    }
    // It was a query or mutation. We send data and complete the operation
    await this.messageSender(connectionID, new GraphQLData(message.id, result))
    return new GraphQLComplete(message.id)
  }
}
