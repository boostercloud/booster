import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  GraphQLRequestEnvelope,
  InvalidProtocolError,
} from '@boostercloud/framework-types'
import { getOperationAST, GraphQLSchema, printSchema, subscribe, parse, execute, DocumentNode, validate } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'

export class BoosterGraphQLDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphQLGenerator(
      config,
      new BoosterCommandDispatcher(config, logger),
      new BoosterReadModelDispatcher(config, logger)
    ).generateSchema()
    console.log(printSchema(this.graphQLSchema))
  }

  public async dispatchGraphQL(request: any): Promise<any> {
    try {
      const envelope = await this.config.provider.rawGraphQLRequestToEnvelope(request, this.logger)
      this.logger.debug('Received the following GraphQL envelope: ', envelope)

      switch (envelope.eventType) {
        case 'CONNECT': // TODO: This message is never coming now. Check this later to see if it is finally needed
          return this.config.provider.handleGraphQLResult()
        case 'MESSAGE':
          return this.config.provider.handleGraphQLResult(await this.handleMessage(envelope))
        case 'DISCONNECT':
          return this.config.provider.handleGraphQLResult()
        default:
          throw new Error(`Unknown message type ${envelope.eventType}`)
      }
    } catch (e) {
      this.logger.error(e)
      return this.config.provider.handleGraphQLError(e)
    }
  }

  private async handleMessage(envelope: GraphQLRequestEnvelope): Promise<any> {
    this.logger.debug('Starting GraphQL query')
    if (!envelope.value) {
      throw new InvalidParameterError('Received an empty GraphQL body')
    }
    const queryDocument = parse(envelope.value)
    const errors = validate(this.graphQLSchema, queryDocument)
    this.throwIfGraphQLErrors(errors)
    const operationData = getOperationAST(queryDocument, undefined)
    if (!operationData) {
      throw new InvalidParameterError(
        'Could not extract GraphQL root operation. Be sure to send only one of {query, mutation, subscription}'
      )
    }

    switch (operationData.operation) {
      case 'query':
      case 'mutation':
        return this.handleQueryOrMutation(envelope, queryDocument)
      case 'subscription':
        return this.handleSubscription(envelope, queryDocument)
    }
  }

  private async handleQueryOrMutation(envelope: GraphQLRequestEnvelope, queryDocument: DocumentNode): Promise<any> {
    if (cameThroughSocket(envelope)) {
      throw new InvalidProtocolError(
        'This API and protocol does not support "query" or "mutation" operations, only "subscription". Use the HTTP API for "query" or "mutation"'
      )
    }
    const result = await execute({
      schema: this.graphQLSchema,
      document: queryDocument,
      contextValue: envelope,
    })
    this.throwIfGraphQLErrors(result.errors)
    this.logger.debug('GraphQL result: ', result.data)
    return result.data
  }

  private async handleSubscription(envelope: GraphQLRequestEnvelope, queryDocument: DocumentNode): Promise<any> {
    if (!cameThroughSocket(envelope)) {
      throw new InvalidProtocolError(
        'This API and protocol does not support "subscription" operations, only "query" and "mutation". Use the socket API for "subscription"'
      )
    }
    const result = await subscribe({
      schema: this.graphQLSchema,
      document: queryDocument,
      contextValue: envelope,
    })
    if ('errors' in result) {
      this.throwIfGraphQLErrors(result.errors)
    }
    this.logger.debug('GraphQL subscription finished')
    return result
  }

  private throwIfGraphQLErrors(errors?: ReadonlyArray<Error>): void {
    // We could have multiple errors, but there is not way to merge errors and keep its stack traces, so we
    // just throw the first error
    const firstError = errors?.[0]
    if (firstError) {
      throw firstError
    }
  }
}

function cameThroughSocket(envelope: GraphQLRequestEnvelope): boolean {
  return envelope.connectionID != undefined
}
