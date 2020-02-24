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
    await graphql(this.graphQLSchema, '{ Cart(id: "demo") { id, paid } }').then((response) => {
      this.logger.info(response)
    })
    return null
  }
}
