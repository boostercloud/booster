import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  GraphQLRequestEnvelope,
  InvalidProtocolError,
  GraphQLOperation,
  GraphQLRequestEnvelopeError,
} from '@boostercloud/framework-types'
import { GraphQLSchema, DocumentNode, ExecutionResult, GraphQLError } from 'graphql'
import * as graphql from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterReadModelsReader } from './booster-read-models-reader'
import { GraphQLResolverContext, graphQLWebsocketSubprotocolHeaders } from './services/graphql/common'
import { NoopReadModelPubSub } from './services/pub-sub/noop-read-model-pub-sub'
import { GraphQLWebsocketHandler } from './services/graphql/websocket-protocol/graphql-websocket-protocol'
import { BoosterTokenVerifier } from './booster-token-verifier'

type DispatchResult = AsyncIterableIterator<ExecutionResult> | ExecutionResult | void

export class BoosterGraphQLDispatcher {
  private readonly graphQLSchema: GraphQLSchema
  private readonly websocketHandler: GraphQLWebsocketHandler
  private readonly readModelDispatcher: BoosterReadModelsReader
  private readonly boosterTokenVerifier: BoosterTokenVerifier

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.readModelDispatcher = new BoosterReadModelsReader(config, logger)
    this.graphQLSchema = GraphQLGenerator.build(config, logger).generateSchema()
    this.boosterTokenVerifier = new BoosterTokenVerifier(config)
    this.websocketHandler = new GraphQLWebsocketHandler(
      config,
      logger,
      this.config.provider.connections,
      {
        onStartOperation: this.runGraphQLOperation.bind(this),
        onStopOperation: this.readModelDispatcher.unsubscribe.bind(this.readModelDispatcher),
        onTerminate: this.handleDisconnect.bind(this),
      },
      this.boosterTokenVerifier
    )
  }

  public async dispatch(request: unknown): Promise<unknown> {
    const envelopeOrError = await this.config.provider.graphQL.rawToEnvelope(request, this.logger, this.config)
    this.logger.debug('Received the following GraphQL envelope: ', envelopeOrError)

    switch (envelopeOrError.eventType) {
      case 'CONNECT':
        return this.config.provider.graphQL.handleResult(null, graphQLWebsocketSubprotocolHeaders)
      case 'MESSAGE':
        return this.config.provider.graphQL.handleResult(await this.handleMessage(envelopeOrError))
      case 'DISCONNECT':
        return this.config.provider.graphQL.handleResult(await this.handleDisconnect(envelopeOrError.connectionID))
      default:
        return this.config.provider.graphQL.handleResult({
          errors: [new Error(`Unknown message type ${envelopeOrError.eventType}`)],
        })
    }
  }

  private async verifyTokenFromEnvelop(
    envelope: GraphQLRequestEnvelope
  ): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
    if (envelope.token) {
      try {
        this.logger.debug(`Decoding current user from auth token: ${envelope.token}`)
        envelope.currentUser = await this.boosterTokenVerifier.verify(envelope.token)
      } catch (e) {
        envelope = {
          ...envelope,
          error: new InvalidParameterError(e),
        } as GraphQLRequestEnvelopeError
        this.logger.debug('Unable to decode auth token')
      }
    }
    return envelope
  }

  private async handleMessage(envelope: GraphQLRequestEnvelope | GraphQLRequestEnvelopeError): Promise<DispatchResult> {
    this.logger.debug(`Starting GraphQL operation: ${JSON.stringify(envelope)}`)

    const envelopeOrError = await this.verifyTokenFromEnvelop(envelope)

    if (cameThroughSocket(envelopeOrError)) {
      return this.websocketHandler.handle(envelopeOrError)
    }
    return this.runGraphQLOperation(envelopeOrError)
  }

  private async runGraphQLOperation(
    envelope: GraphQLRequestEnvelope | GraphQLRequestEnvelopeError
  ): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    try {
      if ('error' in envelope) {
        throw envelope.error
      }
      if (!envelope.value) {
        throw new InvalidParameterError('Received an empty GraphQL body')
      }
      const operation = envelope.value as GraphQLOperation
      if (!operation.query) {
        throw new InvalidParameterError('Received an empty GraphQL query')
      }

      const queryDocument = graphql.parse(operation.query)
      const errors = graphql.validate(this.graphQLSchema, queryDocument)
      if (errors.length > 0) {
        throw errors
      }
      const operationData = graphql.getOperationAST(queryDocument, operation.operationName)
      if (!operationData) {
        throw new InvalidParameterError(
          'Could not extract GraphQL operation. ' +
            'Be sure to either send only one query, mutation, or subscription, or, in case you send several operations, ' +
            'include the "operationName" field'
        )
      }
      const resolverContext: GraphQLResolverContext = {
        connectionID: envelope.connectionID,
        requestID: envelope.requestID,
        user: envelope.currentUser,
        operation: {
          ...operation,
        },
        pubSub: new NoopReadModelPubSub(),
        storeSubscriptions: true,
      }

      switch (operationData.operation) {
        case 'query':
        case 'mutation':
          return await this.handleQueryOrMutation(queryDocument, resolverContext)
        case 'subscription':
          return await this.handleSubscription(queryDocument, resolverContext)
      }
    } catch (e) {
      this.logger.error(e)
      const errors = Array.isArray(e) ? e : [new GraphQLError(e.message)]
      return { errors }
    }
  }

  private async handleQueryOrMutation(
    queryDocument: DocumentNode,
    resolverContext: GraphQLResolverContext
  ): Promise<ExecutionResult> {
    const result = await graphql.execute({
      schema: this.graphQLSchema,
      document: queryDocument,
      contextValue: resolverContext,
      variableValues: resolverContext.operation.variables,
      operationName: resolverContext.operation.operationName,
    })
    this.logger.debug('GraphQL result: ', result)
    return result
  }

  private async handleSubscription(
    queryDocument: DocumentNode,
    resolverContext: GraphQLResolverContext
  ): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    if (!cameThroughSocket(resolverContext)) {
      throw new InvalidProtocolError(
        'This API and protocol does not support "subscription" operations, only "query" and "mutation". Use the socket API for "subscription"'
      )
    }
    const result = await graphql.subscribe({
      schema: this.graphQLSchema,
      document: queryDocument,
      contextValue: resolverContext,
      variableValues: resolverContext.operation.variables,
      operationName: resolverContext.operation.operationName,
    })
    this.logger.debug('GraphQL subscription finished')
    return result
  }

  private async handleDisconnect(connectionID?: string): Promise<void> {
    if (!connectionID) {
      // This should be impossible, but just in case
      this.logger.info("Received a DISCONNECT message but field 'connectionID' is missing. Doing nothing")
      return
    }
    this.logger.debug('Deleting all subscriptions and connection data')
    await this.config.provider.connections.deleteData(this.config, connectionID)
    await this.readModelDispatcher.unsubscribeAll(connectionID)
  }
}

function cameThroughSocket(withConnectionID: { connectionID?: string }): boolean {
  return withConnectionID.connectionID != undefined
}
