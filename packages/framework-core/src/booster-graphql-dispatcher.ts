import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { graphql, GraphQLSchema } from 'graphql'
import { GraphqlGenerator } from './services/graphql-generator'

export class BoosterGraphqlDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphqlGenerator(this.config).generateSchema()
    // console.log(printSchema(this.graphQLSchema))
  }

  public async dispatchGraphQL(request: any): Promise<any> {
    const envelope = await this.config.provider.rawGraphQLRequestToEnvelope(request, this.logger)
    this.logger.debug('Received the following GraphQL envelope: ', envelope)

    switch (envelope.eventType) {
      case 'CONNECT': // TODO: This message is never coming now. Check this later to see if it is finally needed
        return this.config.provider.handleGraphQLResult()
      case 'MESSAGE':
        // Handle queries, mutations and subscriptions here
        // Do the queries and commands we have right now and finish. Then keep working
        return this.config.provider.handleGraphQLResult({
          connectionID: envelope.connectionID,
        })
      case 'DISCONNECT':
        return this.config.provider.handleGraphQLResult()
    }

    const result = await graphql(this.graphQLSchema, '')
    if (result.errors) {
      const error = new Error(result.errors.map((e) => e.message).join('\n'))
      return this.config.provider.handleGraphQLError(error)
    }
    return this.config.provider.handleGraphQLResult(result.data)
  }
}
