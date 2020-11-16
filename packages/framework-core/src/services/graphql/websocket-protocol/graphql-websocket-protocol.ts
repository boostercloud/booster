import { ExecutionResult } from 'graphql'
import {
  BoosterConfig,
  ConnectionDataEnvelope,
  GraphQLClientMessage,
  GraphQLComplete,
  GraphQLData,
  GraphQLError,
  GraphQLInit,
  GraphQLInitAck,
  GraphQLInitError,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  GraphQLStart,
  GraphQLStop,
  Logger,
  MessageTypes,
  ProviderConnectionsLibrary,
  UserEnvelope,
} from '@boostercloud/framework-types'
import { BoosterTokenVerifier } from '../../../booster-token-verifier'

export interface GraphQLWebsocketHandlerCallbacks {
  onStartOperation: (
    envelope: GraphQLRequestEnvelope
  ) => Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult>
  onStopOperation: (connectionID: string, messageID: string) => Promise<void>
  onTerminate: (connectionID: string) => Promise<void>
}

export class GraphQLWebsocketHandler {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly logger: Logger,
    private readonly connectionManager: ProviderConnectionsLibrary,
    private readonly callbacks: GraphQLWebsocketHandlerCallbacks,
    private readonly boosterTokenVerifier: BoosterTokenVerifier
  ) {}

  public async handle(envelope: GraphQLRequestEnvelope | GraphQLRequestEnvelopeError): Promise<void> {
    if (!envelope.connectionID) {
      // Impossible case, but just to be sure. The only thing we can do here is to log, as we don't have the connection
      // to send the message to
      this.logger.error('Missing websocket connectionID')
      return
    }

    try {
      this.logger.debug('Handling websocket message')
      if ('error' in envelope) {
        throw envelope.error
      }
      if (!envelope.value) {
        throw new Error('Received an empty GraphQL body')
      }

      const clientMessage = envelope.value as GraphQLClientMessage
      this.logger.debug('Received client message: ', clientMessage)
      switch (clientMessage.type) {
        case MessageTypes.GQL_CONNECTION_INIT:
          return await this.handleInit(envelope.connectionID, clientMessage)
        case MessageTypes.GQL_START:
          return await this.handleStart(envelope.connectionID, envelope, clientMessage)
        case MessageTypes.GQL_STOP:
          return await this.handleStop(envelope.connectionID, clientMessage)
        case MessageTypes.GQL_CONNECTION_TERMINATE:
          return await this.handleTerminate(envelope.connectionID)
        default:
          // This branch should be impossible, but just in case
          throw new Error(`Unknown message type. Message=${clientMessage}`)
      }
    } catch (e) {
      this.logger.error(e)
      await this.connectionManager.sendMessage(this.config, envelope.connectionID, new GraphQLInitError(e.message))
    }
  }

  private async handleInit(connectionID: string, clientMessage: GraphQLInit): Promise<void> {
    let userEnvelope: UserEnvelope | undefined
    if (clientMessage.payload?.Authorization) {
      userEnvelope = await this.boosterTokenVerifier.verify(clientMessage.payload.Authorization)
    }
    const nowEpoch = Math.floor(new Date().getTime() / 1000)
    const connectionData: ConnectionDataEnvelope = {
      expirationTime: nowEpoch + this.config.subscriptions.maxConnectionDurationInSeconds,
      user: userEnvelope,
    }
    this.logger.debug('Storing connection data: ', connectionData)
    await this.connectionManager.storeData(this.config, connectionID, connectionData)
    this.logger.debug('Sending ACK')
    await this.connectionManager.sendMessage(this.config, connectionID, new GraphQLInitAck())
  }

  private async handleStart(
    connectionID: string,
    envelope: GraphQLRequestEnvelope,
    message: GraphQLStart
  ): Promise<void> {
    if (!message.id) {
      throw new Error(`Missing "id" in ${message.type} message`)
    }
    if (!message.payload || !message.payload.query) {
      await this.connectionManager.sendMessage(
        this.config,
        connectionID,
        new GraphQLError(message.id, {
          errors: [new Error('Message payload is invalid it must contain at least the "query" property')],
        })
      )
      return
    }
    const augmentedEnvelope = await this.augmentEnvelope(connectionID, envelope, message)

    this.logger.debug('Executing operation. Envelope: ', augmentedEnvelope)
    const result = await this.callbacks.onStartOperation(augmentedEnvelope)

    if ('next' in result) {
      this.logger.debug('Subscription finished.')
      return // It was a subscription. We don't need to send any data
    }

    this.logger.debug('Operation finished. Sending DATA:', result)
    // It was a query or mutation. We send data and complete the operation
    await this.connectionManager.sendMessage(this.config, connectionID, new GraphQLData(message.id, result))
    await this.connectionManager.sendMessage(this.config, connectionID, new GraphQLComplete(message.id))
  }

  private async augmentEnvelope(
    connectionID: string,
    envelope: GraphQLRequestEnvelope,
    message: GraphQLStart
  ): Promise<GraphQLRequestEnvelope> {
    const connectionData = await this.connectionManager.fetchData(this.config, connectionID)
    this.logger.debug('Found connection data: ', connectionData)
    return {
      ...envelope,
      currentUser: connectionData?.user,
      value: {
        ...message.payload,
        id: message.id,
      },
    }
  }

  private async handleStop(connectionID: string, message: GraphQLStop): Promise<void> {
    if (!message.id) {
      throw new Error(`Missing "id" in ${message.type} message`)
    }

    this.logger.debug('Executing stop operation')
    await this.callbacks.onStopOperation(connectionID, message.id)
    this.logger.debug('Stop operation finished')
    await this.connectionManager.sendMessage(this.config, connectionID, new GraphQLComplete(message.id))
  }

  private async handleTerminate(connectionID: string): Promise<void> {
    this.logger.debug('Executing terminate operation')
    await this.callbacks.onTerminate(connectionID)
    this.logger.debug('Terminate operation finished')
  }
}
