import { BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLFunctionDefinition } from '../types/functionDefinition'

export class GraphqlFunction {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinition(): GraphQLFunctionDefinition {
    return {
      name: 'graphql',
      config: {
        bindings: [
          {
            authLevel: 'anonymous',
            type: 'httpTrigger',
            direction: 'in',
            name: 'rawRequest',
            methods: ['post'],
          },
          {
            type: 'http',
            direction: 'out',
            name: '$return',
          },
        ],
        scriptFile: '../dist/index.js',
        entryPoint: this.config.serveGraphQLHandler.split('.')[1],
      },
    }
  }
}
