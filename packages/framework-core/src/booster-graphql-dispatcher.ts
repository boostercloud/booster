import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { graphql, buildSchema } from 'graphql'

// Construct a schema, using GraphQL schema language
const schema = buildSchema(`
  type Query {
    hello: String
  }
`)

// The root provides a resolver function for each API endpoint
const root = {
  hello: () => 'Hello world!',
}

export class BoosterGraphqlDispatcher {
  // @ts-ignore
  public constructor(private config: BoosterConfig, private logger: Logger) {}

  public async dispatchGraphQL(request: any): Promise<any> {
    await graphql(schema, '{ hello }', root).then((response) => {
      this.logger.info(response)
    })
    return null
  }
}
