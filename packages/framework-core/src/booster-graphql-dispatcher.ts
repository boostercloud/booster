import { BoosterConfig, Logger, InvalidParameterError, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { graphql, GraphQLSchema } from 'graphql'
import { GraphqlGenerator } from './services/graphql-generator'

export class BoosterGraphqlDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphqlGenerator(this.config).generateSchema()
  }

  public async dispatchGraphQL(request: any): Promise<any> {
    try {
      const envelope = await this.config.provider.rawGraphQLRequestToEnvelope(request, this.logger)
      this.logger.debug('Received the following GraphQL envelope: ', envelope)

      // TODO: We should rejects requests for query and mutation that come through sockets and subscriptions that comes through REST

      // Keep working with the schema generation to allow mutations and queries with basic filtering.

      switch (envelope.eventType) {
        case 'CONNECT': // TODO: This message is never coming now. Check this later to see if it is finally needed
          return this.config.provider.handleGraphQLResult()
        case 'MESSAGE':
          return this.handleMessage(envelope)
        case 'DISCONNECT':
          return this.config.provider.handleGraphQLResult()
      }
    } catch (e) {
      return this.config.provider.handleGraphQLError(e)
    }
  }

  private async handleMessage(envelope: GraphQLRequestEnvelope): Promise<any> {
    if (!envelope.value) {
      throw new InvalidParameterError('Received an empty GraphQL body')
    }

    this.logger.debug('Starting GraphQL query')
    const result = await graphql(this.graphQLSchema, envelope.value)
    this.logger.debug('GraphQL result: ', result)
    if (result.errors) {
      const error = new Error(result.errors.map((e) => e.message).join('\n'))
      this.logger.error(error)
      return this.config.provider.handleGraphQLError(error)
    }
    return this.config.provider.handleGraphQLResult(result.data)

    // TODO: We need to send the result to all related subscriptions. Find an abstraction that allow us to manage
    // connections here and search for those which are subscribed to a subscription
  }
}
