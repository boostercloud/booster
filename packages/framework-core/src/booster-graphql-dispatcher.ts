import { BoosterConfig, Logger, InvalidParameterError, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { graphql, GraphQLSchema } from 'graphql'
import { GraphqlGenerator } from './services/graphql-generator'

export class BoosterGraphqlDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphqlGenerator(this.config).generateSchema()
  }

  public async dispatchGraphQL(request: any): Promise<any> {
    const envelope = await this.config.provider.rawGraphQLRequestToEnvelope(request, this.logger)
    this.logger.debug('Received the following GraphQL envelope: ', envelope)

    switch (envelope.eventType) {
      case 'CONNECT': // TODO: This message is never coming now. Check this later to see if it is finally needed
        return this.config.provider.handleGraphQLResult()
      case 'MESSAGE':
        return this.handleMessage(envelope)
      case 'DISCONNECT':
        return this.config.provider.handleGraphQLResult()
    }
  }

  private async handleMessage(envelope: GraphQLRequestEnvelope): Promise<any> {
    if (!envelope.value) {
      throw new InvalidParameterError('Received an empty GraphQL body')
    }

    const result = await graphql(this.graphQLSchema, envelope.value)
    if (result.errors) {
      const error = new Error(result.errors.map((e) => e.message).join('\n'))
      this.logger.error(error)
      return this.config.provider.handleGraphQLError(error)
    }
    return this.config.provider.handleGraphQLResult(result.data)

    // TODO: We need to send the result to all related subscriptions
  }
}
