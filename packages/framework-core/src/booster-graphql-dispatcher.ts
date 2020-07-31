import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  GraphQLRequestEnvelope,
  InvalidProtocolError,
  GraphQLOperation,
} from '@boostercloud/framework-types'
import { GraphQLSchema, DocumentNode, ExecutionResult, GraphQLError } from 'graphql'
import * as graphql from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'
import { GraphQLResolverContext, graphQLWebsocketSubprotocolHeaders } from './services/graphql/common'
import { NoopReadModelPubSub } from './services/pub-sub/noop-read-model-pub-sub'
import { GraphQLWebsocketHandler } from './services/graphql/websocket-protocol/graphql-websocket-protocol'

type DispatchResult = AsyncIterableIterator<ExecutionResult> | ExecutionResult | void

export class BoosterGraphQLDispatcher {
  private readonly graphQLSchema: GraphQLSchema
  private readonly websocketHandler: GraphQLWebsocketHandler
  private readonly readModelDispatcher: BoosterReadModelDispatcher

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.readModelDispatcher = new BoosterReadModelDispatcher(config, logger)
    const commandDispatcher = new BoosterCommandDispatcher(config, logger)

    this.graphQLSchema = new GraphQLGenerator(config, commandDispatcher, this.readModelDispatcher).generateSchema()
    this.websocketHandler = new GraphQLWebsocketHandler(
      config,
      logger,
      this.config.provider.auth,
      this.config.provider.connections,
      {
        onStartOperation: this.runGraphQLOperation.bind(this),
        onStopOperation: this.readModelDispatcher.unsubscribe.bind(this.readModelDispatcher),
        onTerminate: this.readModelDispatcher.unsubscribeAll.bind(this.readModelDispatcher),
      }
    )
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async dispatch(request: any): Promise<any> {
    const envelope = await this.config.provider.graphQL.rawToEnvelope(request, this.logger)
    this.logger.debug('Received the following GraphQL envelope: ', envelope)

    switch (envelope.eventType) {
      case 'CONNECT':
        return this.config.provider.graphQL.handleResult(null, graphQLWebsocketSubprotocolHeaders)
      case 'MESSAGE':
        return this.config.provider.graphQL.handleResult(await this.handleMessage(envelope))
      case 'DISCONNECT':
        return this.config.provider.graphQL.handleResult(await this.handleDisconnect(envelope))
      default:
        return this.config.provider.graphQL.handleResult({
          errors: [new Error(`Unknown message type ${envelope.eventType}`)],
        })
    }
  }

  private async handleMessage(envelope: GraphQLRequestEnvelope): Promise<DispatchResult> {
    this.logger.debug('Starting GraphQL operation')
    if (cameThroughSocket(envelope)) {
      return this.websocketHandler.handle(envelope)
    }
    return this.runGraphQLOperation(envelope)
  }

  private async runGraphQLOperation(
    envelope: GraphQLRequestEnvelope
  ): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    try {
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

  private async handleDisconnect(envelope: GraphQLRequestEnvelope): Promise<void> {
    if (!envelope.connectionID) {
      // This should be impossible, but just in case
      this.logger.debug("Received a DISCONNECT message but field 'connectionID' is missing. Doing nothing")
      return
    }
    return this.readModelDispatcher.unsubscribeAll(envelope.connectionID)
  }
}

function cameThroughSocket(withConnectionID: { connectionID?: string }): boolean {
  return withConnectionID.connectionID != undefined
}
