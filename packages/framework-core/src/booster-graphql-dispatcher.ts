/* eslint-disable no-case-declarations */
import {
  BoosterConfig,
  InvalidParameterError,
  GraphQLRequestEnvelope,
  InvalidProtocolError,
  GraphQLOperation,
  GraphQLRequestEnvelopeError,
  graphQLWebsocketSubprotocolHeaders,
  TraceActionTypes,
} from '@boostercloud/framework-types'
import { GraphQLSchema, DocumentNode, ExecutionResult, GraphQLError, OperationTypeNode } from 'graphql'
import * as graphql from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterReadModelsReader } from './booster-read-models-reader'
import { GraphQLResolverContext } from './services/graphql/common'
import { NoopReadModelPubSub } from './services/pub-sub/noop-read-model-pub-sub'
import { GraphQLWebsocketHandler } from './services/graphql/websocket-protocol/graphql-websocket-protocol'
import { BoosterTokenVerifier } from './booster-token-verifier'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { Trace } from './instrumentation'

type DispatchResult = AsyncIterableIterator<ExecutionResult> | ExecutionResult | void

export class BoosterGraphQLDispatcher {
  private readonly graphQLSchema: GraphQLSchema
  private readonly websocketHandler: GraphQLWebsocketHandler
  private readonly readModelDispatcher: BoosterReadModelsReader
  private readonly boosterTokenVerifier: BoosterTokenVerifier

  public constructor(private config: BoosterConfig) {
    this.readModelDispatcher = new BoosterReadModelsReader(config)
    this.graphQLSchema = GraphQLGenerator.generateSchema(config)
    this.boosterTokenVerifier = new BoosterTokenVerifier(config)
    this.websocketHandler = new GraphQLWebsocketHandler(
      config,
      this.config.provider.connections,
      {
        onStartOperation: this.runGraphQLOperation.bind(this),
        onStopOperation: this.readModelDispatcher.unsubscribe.bind(this.readModelDispatcher),
        onTerminate: this.handleDisconnect.bind(this),
      },
      this.boosterTokenVerifier
    )
  }

  @Trace(TraceActionTypes.GRAPHQL_DISPATCH)
  public async dispatch(request: unknown): Promise<unknown> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#dispatch')
    const envelopeOrError = await this.config.provider.graphQL.rawToEnvelope(this.config, request)
    logger.debug('Received the following GraphQL envelope: ', envelopeOrError)

    switch (envelopeOrError.eventType) {
      case 'CONNECT':
        return this.config.provider.graphQL.handleResult(null, graphQLWebsocketSubprotocolHeaders)
      case 'MESSAGE':
        const responseHeaders = { ...this.config.defaultResponseHeaders }
        const result = await this.handleMessage(envelopeOrError, responseHeaders)
        return this.config.provider.graphQL.handleResult(result, responseHeaders)
      case 'DISCONNECT':
        return this.config.provider.graphQL.handleResult(await this.handleDisconnect(envelopeOrError.connectionID))
      default:
        return this.config.provider.graphQL.handleResult({
          errors: [new Error(`Unknown message type ${envelopeOrError.eventType}`)],
        })
    }
  }

  private async verifyTokenFromEnvelope(
    envelope: GraphQLRequestEnvelope
  ): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#verifyTokenFromEnvelop')
    if (envelope.token) {
      try {
        logger.debug(`Decoding current user from auth token: ${envelope.token}`)
        envelope.currentUser = await this.boosterTokenVerifier.verify(envelope.token)
      } catch (e) {
        envelope = {
          ...envelope,
          error: e,
        } as GraphQLRequestEnvelopeError
        logger.debug('Unable to decode auth token')
      }
    }
    return envelope
  }

  private async handleMessage(
    envelope: GraphQLRequestEnvelope | GraphQLRequestEnvelopeError,
    responseHeaders: Record<string, string>
  ): Promise<DispatchResult> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#handleMessage')
    logger.debug('Starting GraphQL operation:', envelope)

    const envelopeOrError = await this.verifyTokenFromEnvelope(envelope)

    if (cameThroughSocket(envelopeOrError)) {
      return this.websocketHandler.handle(envelopeOrError)
    }
    return this.runGraphQLOperation(envelopeOrError, responseHeaders)
  }

  @Trace(TraceActionTypes.GRAPHQL_RUN_OPERATION)
  private async runGraphQLOperation(
    envelope: GraphQLRequestEnvelope | GraphQLRequestEnvelopeError,
    responseHeaders: Record<string, string> = {}
  ): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#runGraphQLOperation')
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
      const isIntrospectionQuery =
        operation.operationName === 'IntrospectionQuery' || operation.query.includes('__schema')
      if (isIntrospectionQuery && !this.config.enableGraphQLIntrospection) {
        throw new InvalidProtocolError(
          'Instrospection queries are disabled. Check the configuration if you want to enable them.'
        )
      }

      if (!operationData) {
        throw new InvalidParameterError(
          'Could not extract GraphQL operation. ' +
            'Be sure to either send only one query, mutation, or subscription, or, in case you send several operations, ' +
            'include the "operationName" field'
        )
      }
      const resolverContext: GraphQLResolverContext = {
        connectionID: envelope.connectionID,
        responseHeaders: responseHeaders,
        requestID: envelope.requestID,
        user: envelope.currentUser,
        operation: {
          ...operation,
        },
        pubSub: new NoopReadModelPubSub(),
        storeSubscriptions: true,
        context: envelope.context,
      }

      switch (operationData.operation) {
        case OperationTypeNode.QUERY:
        case OperationTypeNode.MUTATION:
          return await this.handleQueryOrMutation(queryDocument, resolverContext)
        case OperationTypeNode.SUBSCRIPTION:
          return await this.handleSubscription(queryDocument, resolverContext)
      }
    } catch (e) {
      const error = e as Error
      logger.error(e)
      const errors = Array.isArray(e) ? e.map(toGraphQLErrorWithExtensions) : [toGraphQLErrorWithExtensions(error)]
      return { errors }
    }
  }

  private async handleQueryOrMutation(
    queryDocument: DocumentNode,
    resolverContext: GraphQLResolverContext
  ): Promise<ExecutionResult> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#handleQueryOrMutation')
    const result = await graphql.execute({
      schema: this.graphQLSchema,
      document: queryDocument,
      contextValue: resolverContext,
      variableValues: resolverContext.operation.variables,
      operationName: resolverContext.operation.operationName,
    })
    result.errors = result.errors?.map(toGraphQLErrorWithExtensions)
    logger.debug('GraphQL result: ', result)
    return result
  }

  private async handleSubscription(
    queryDocument: DocumentNode,
    resolverContext: GraphQLResolverContext
  ): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#handleSubscription')
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
    logger.debug('GraphQL subscription finished')
    return result
  }

  private async handleDisconnect(connectionID?: string): Promise<void> {
    const logger = getLogger(this.config, 'BoosterGraphQLDispatcher#handleDisconnect')
    if (!connectionID) {
      // This should be impossible, but just in case
      logger.info("Received a DISCONNECT message but field 'connectionID' is missing. Doing nothing")
      return
    }
    logger.debug('Deleting all subscriptions and connection data')
    await this.config.provider.connections.deleteData(this.config, connectionID)
    await this.readModelDispatcher.unsubscribeAll(connectionID)
  }
}

function cameThroughSocket(withConnectionID: { connectionID?: string }): boolean {
  return withConnectionID.connectionID != undefined
}

type BoosterError = Error & { code?: unknown; data?: unknown }
function toGraphQLErrorWithExtensions(e: BoosterError | GraphQLError): GraphQLError {
  if (e instanceof GraphQLError) {
    const originalError = e.originalError as BoosterError
    return new GraphQLError(e.message, e.nodes, e.source, e.positions, e.path, originalError, {
      code: originalError?.code,
      data: originalError?.data,
    })
  }
  return new GraphQLError(e.message, undefined, undefined, undefined, undefined, e, {
    code: e.code,
    data: e.data,
  })
}
