import { BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLFunctionDefinition } from '../types/functionDefinition'
import { functionPath } from '../utils'

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
        scriptFile: functionPath(this.config),
        entryPoint: this.config.serveGraphQLHandler.split('.')[1],
      },
    }
  }
}
