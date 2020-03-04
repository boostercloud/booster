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
      case 'CONNECT':
        return this.config.provider.handleGraphQLResult()
        break
      case 'MESSAGE':
        return this.config.provider.handleGraphQLResult({
          connectionID: envelope.connectionID,
        })
        break
      case 'DISCONNECT':
        break
    }

    const result = await graphql(this.graphQLSchema, '')
    if (result.errors) {
      const error = new Error(result.errors.map((e) => e.message).join('\n'))
      return this.config.provider.handleGraphQLError(error)
    }
    return this.config.provider.handleGraphQLResult(result.data)
  }
}
