import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  GraphQLRequestEnvelope,
  InvalidProtocolError,
} from '@boostercloud/framework-types'
import { GraphQLSchema, DocumentNode, ExecutionResult } from 'graphql'
import * as graphql from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'
import { GraphQLResolverContext } from './services/graphql/common'
import { NoopReadModelPubSub } from './services/pub-sub/noop-read-model-pub-sub'

export class BoosterGraphQLDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphQLGenerator(
      config,
      new BoosterCommandDispatcher(config, logger),
      new BoosterReadModelDispatcher(config, logger)
    ).generateSchema()
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async dispatch(request: any): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    try {
      const envelope = await this.config.provider.rawGraphQLRequestToEnvelope(request, this.logger)
      this.logger.debug('Received the following GraphQL envelope: ', envelope)

      switch (envelope.eventType) {
        case 'CONNECT': // TODO: This message is never coming now. Check this later to see if it is finally needed
          return this.config.provider.handleGraphQLResult()
        case 'MESSAGE':
          return this.config.provider.handleGraphQLResult(await this.handleMessage(envelope))
        case 'DISCONNECT':
          // TODO: Remove subscriptions
          return this.config.provider.handleGraphQLResult()
        default:
          throw new Error(`Unknown message type ${envelope.eventType}`)
      }
    } catch (e) {
      this.logger.error(e)
      const toErrors = (e: Error): Array<Partial<graphql.GraphQLError>> => [
        {
          message: JSON.stringify(e),
          locations: [],
        },
      ]
      const errors = Array.isArray(e) ? e : toErrors(e)
      return this.config.provider.handleGraphQLResult({ errors })
    }
  }

  private async handleMessage(
    envelope: GraphQLRequestEnvelope
  ): Promise<AsyncIterableIterator<ExecutionResult> | ExecutionResult> {
    this.logger.debug('Starting GraphQL query')
    if (!envelope.value) {
      throw new InvalidParameterError('Received an empty GraphQL body')
    }
    const queryDocument = graphql.parse(envelope.value)
    const errors = graphql.validate(this.graphQLSchema, queryDocument)
    if (errors) {
      throw errors
    }
    const operationData = graphql.getOperationAST(queryDocument, undefined)
    if (!operationData) {
      throw new InvalidParameterError(
        'Could not extract GraphQL root operation. Be sure to send only one of {query, mutation, subscription}'
      )
    }
    const resolverContext: GraphQLResolverContext = {
      connectionID: envelope.connectionID,
      requestID: envelope.requestID,
      user: envelope.currentUser,
      operation: {
        query: envelope.value,
        variables: envelope.variables,
      },
      pubSub: new NoopReadModelPubSub(),
      storeSubscriptions: true,
    }

    switch (operationData.operation) {
      case 'query':
      case 'mutation':
        return this.handleQueryOrMutation(queryDocument, resolverContext)
      case 'subscription':
        return this.handleSubscription(queryDocument, resolverContext)
    }
  }

  private async handleQueryOrMutation(
    queryDocument: DocumentNode,
    resolverContext: GraphQLResolverContext
  ): Promise<ExecutionResult> {
    if (cameThroughSocket(resolverContext)) {
      throw new InvalidProtocolError(
        'This API and protocol does not support "query" or "mutation" operations, only "subscription". Use the HTTP API for "query" or "mutation"'
      )
    }
    const result = await graphql.execute({
      schema: this.graphQLSchema,
      document: queryDocument,
      contextValue: resolverContext,
    })
    this.logger.debug('GraphQL result: ', result.data)
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
    })
    this.logger.debug('GraphQL subscription finished')
    return result
  }
}

function cameThroughSocket(withConnectionID: { connectionID?: string }): boolean {
  return withConnectionID.connectionID != undefined
}
