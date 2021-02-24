import {
  AnyClass,
  Logger,
  BoosterConfig,
  CommandEnvelope,
  ReadModelPropertyFilter,
  ReadModelRequestEnvelope,
} from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLSchema } from 'graphql'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import { GraphQLMutationGenerator } from './graphql-mutation-generator'
import { GraphQLSubscriptionGenerator } from './graphql-subcriptions-generator'
import { BoosterCommandDispatcher } from '../../booster-command-dispatcher'
import { BoosterReadModelDispatcher } from '../../booster-read-model-dispatcher'
import { GraphQLResolverContext } from './common'

export class GraphQLGenerator {
  private readonly queryGenerator: GraphQLQueryGenerator
  private readonly mutationGenerator: GraphQLMutationGenerator
  private readonly subscriptionGenerator: GraphQLSubscriptionGenerator
  private readonly typeInformer: GraphQLTypeInformer

  private static singleton: GraphQLGenerator | undefined
  
  public static build(config: BoosterConfig, logger: Logger): GraphQLGenerator {
    this.singleton = this.singleton ?? new GraphQLGenerator(
      config,
      new BoosterCommandDispatcher(config, logger),
      new BoosterReadModelDispatcher(config, logger)
    )
    return this.singleton
  }

  private constructor(
    config: BoosterConfig,
    private commandsDispatcher: BoosterCommandDispatcher,
    private readModelsDispatcher: BoosterReadModelDispatcher
  ) {
    this.typeInformer = new GraphQLTypeInformer({ ...config.readModels, ...config.commandHandlers })
    this.queryGenerator = new GraphQLQueryGenerator(
      config.readModels,
      this.typeInformer,
      this.readModelByIDResolverBuilder.bind(this),
      this.readModelResolverBuilder.bind(this)
    )
    this.mutationGenerator = new GraphQLMutationGenerator(
      config.commandHandlers,
      this.typeInformer,
      this.commandResolverBuilder.bind(this)
    )
    this.subscriptionGenerator = new GraphQLSubscriptionGenerator(
      config.readModels,
      this.typeInformer,
      this.queryGenerator,
      this.subscriptionByIDResolverBuilder.bind(this),
      this.subscriptionResolverBuilder.bind(this)
    )
  }

  public generateSchema(): GraphQLSchema {
    return new GraphQLSchema({
      query: this.queryGenerator.generate(),
      mutation: this.mutationGenerator.generate(),
      subscription: this.subscriptionGenerator.generate(),
    })
  }

  public readModelResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, Record<string, ReadModelPropertyFilter>> {
    return (parent, args, context, info) => {
      const readModelEnvelope = toReadModelRequestEnvelope(readModelClass.name, args, context)
      return this.readModelsDispatcher.fetch(readModelEnvelope)
    }
  }

  public readModelByIDResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, Record<string, ReadModelPropertyFilter>> {
    return async (parent, args, context, info) => {
      const filterArgs = { id: { eq: args.id } }
      const result = await this.readModelResolverBuilder(readModelClass)(parent, filterArgs, context, info)
      return result[0]
    }
  }

  public commandResolverBuilder(
    commandClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, { input: any }> {
    return async (parent, args, context, info) => {
      const commandEnvelope = toCommandEnvelope(commandClass.name, args.input, context)
      await this.commandsDispatcher.dispatchCommand(commandEnvelope)
      return true
    }
  }

  public subscriptionByIDResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, Record<string, ReadModelPropertyFilter>> {
    return async (parent, args, context, info) => {
      const filterArgs = { id: { eq: args.id } }
      return this.subscriptionResolverBuilder(readModelClass)(parent, filterArgs, context, info)
    }
  }

  public subscriptionResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLResolverContext, Record<string, ReadModelPropertyFilter>> {
    return async (parent, args, context, info) => {
      if (!context.connectionID) {
        throw new Error('Missing "connectionID". It is required for subscriptions')
      }

      const readModelRequestEnvelope = toReadModelRequestEnvelope(readModelClass.name, args, context)
      if (context.storeSubscriptions) {
        await this.readModelsDispatcher.subscribe(context.connectionID, readModelRequestEnvelope, context.operation)
      }

      return context.pubSub.asyncIterator(readModelRequestEnvelope)
    }
  }
}

function toReadModelRequestEnvelope(
  readModelName: string,
  args: Record<string, ReadModelPropertyFilter>,
  context: GraphQLResolverContext
): ReadModelRequestEnvelope {
  return {
    requestID: context.requestID,
    currentUser: context.user,
    typeName: readModelName,
    filters: args,
    version: 1, // TODO: How to pass the version through GraphQL?
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
