import { GraphQLResolverContext } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLFieldConfigMap, GraphQLNonNull, GraphQLFieldResolver } from 'graphql'
import { AnyClass, BoosterConfig, CommandEnvelope, Logger } from '@boostercloud/framework-types'
import { BoosterCommandDispatcher } from 'framework-core/src/booster-command-dispatcher'

export class GraphQLCommandMutationGenerator {
  public static generate(config: BoosterConfig, logger: Logger): GraphQLFieldConfigMap<any, any> {
    const commandsDispatcher = new BoosterCommandDispatcher(config, logger)
    const targetTypes = config.commandHandlers
    const typeInformer = new GraphQLTypeInformer({
      ...config.commandHandlers,
    })

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const mutations: GraphQLFieldConfigMap<any, any> = {}
    for (const name in targetTypes) {
      const type = targetTypes[name]
      mutations[name] = {
        type: typeInformer.getGraphQLTypeFor(type.returnClass ?? Boolean),
        args: {
          input: {
            type: new GraphQLNonNull(typeInformer.getGraphQLInputTypeFor(type.class)),
          },
        },
        resolve: commandResolverBuilder.bind(null, commandsDispatcher, type.class),
      }
    }

    return mutations
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function toCommandEnvelope(commandName: string, value: any, context: GraphQLResolverContext): CommandEnvelope {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    typeName: commandName,
    value,
    version: 1, // TODO: How to pass the version through GraphQL?
  }
}

/**
 * Builder method to generate a resolver function for a specific command class.
 * @param commandsDispatcher
 * @param commandClass
 * @returns
 */
function commandResolverBuilder(
  commandsDispatcher: BoosterCommandDispatcher,
  commandClass: AnyClass
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): GraphQLFieldResolver<any, GraphQLResolverContext, { input: any }> {
  return async (_parent, args, context, _info) => {
    const commandEnvelope = toCommandEnvelope(commandClass.name, args.input, context)
    const result = await commandsDispatcher.dispatchCommand(commandEnvelope)
    // It could be that the command didn't return anything
    // so in that case we return `true`, as GraphQL doesn't have a `null` type
    return result ?? true
  }
}
