import { BoosterConfig, Logger, InvalidParameterError, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { graphql, GraphQLSchema } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelFetcher } from './booster-read-model-fetcher'

export class BoosterGraphQLDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphQLGenerator(
      config,
      new BoosterCommandDispatcher(config, logger),
      new BoosterReadModelFetcher(config, logger)
    ).generateSchema()
  }

  public async dispatchGraphQL(request: any): Promise<any> {
    try {
      const envelope = await this.config.provider.rawGraphQLRequestToEnvelope(request, this.logger)
      this.logger.debug('Received the following GraphQL envelope: ', envelope)

      // TODO: We should rejects requests for query and mutation that come through sockets and subscriptions that comes through REST

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
    if (!envelope.value) {
      throw new InvalidParameterError('Received an empty GraphQL body')
    }

    this.logger.debug('Starting GraphQL query')
    const result = await graphql({
      schema: this.graphQLSchema,
      source: envelope.value,
      contextValue: envelope,
    })
    this.logger.debug('GraphQL result: ', result)
    if (result.errors) {
      throw new Error(result.errors.map((e) => e.message).join('\n'))
    }
    return result.data

    // TODO: We need to send the result to all related subscriptions. Find an abstraction that allow us to manage
    // connections here and search for those which are subscribed to a subscription
  }
}
