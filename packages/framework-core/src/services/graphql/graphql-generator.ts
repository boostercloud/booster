import {
  AnyClass,
  BoosterConfig,
  GraphQLRequestEnvelope,
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

export class GraphQLGenerator {
  private queryGenerator: GraphQLQueryGenerator
  private mutationGenerator: GraphQLMutationGenerator
  private subscriptionGenerator: GraphQLSubscriptionGenerator
  private readonly typeInformer: GraphQLTypeInformer

  public constructor(
    config: BoosterConfig,
    private commandsDispatcher: BoosterCommandDispatcher,
    private readModelsDispatcher: BoosterReadModelDispatcher
  ) {
    this.typeInformer = new GraphQLTypeInformer({ ...config.readModels, ...config.commandHandlers })
    this.queryGenerator = new GraphQLQueryGenerator(
      config.readModels,
      this.typeInformer,
      this.readModelByIDResolverBuilder.bind(this),
      this.readModelFilterResolverBuilder.bind(this)
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

  public readModelFilterResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLRequestEnvelope, Record<string, ReadModelPropertyFilter>> {
    return (parent, args, context, info) => {
      const readModelEnvelope: ReadModelRequestEnvelope = {
        requestID: context.requestID,
        currentUser: context.currentUser,
        typeName: readModelClass.name,
        filters: args,
        version: 1, // TODO: How to pass the version through GraphQL?
      }
      return this.readModelsDispatcher.fetch(readModelEnvelope)
    }
  }

  public readModelByIDResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLRequestEnvelope, Record<string, ReadModelPropertyFilter>> {
    return async (parent, args, context, info) => {
      const readModelEnvelope: ReadModelRequestEnvelope = {
        requestID: context.requestID,
        currentUser: context.currentUser,
        typeName: readModelClass.name,
        filters: {
          id: {
            operation: '=',
            values: [args.id],
          },
        },
        version: 1, // TODO: How to pass the version through GraphQL?
      }
      const result = await this.readModelsDispatcher.fetch(readModelEnvelope)
      return result[0]
    }
  }

  public commandResolverBuilder(
    commandClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLRequestEnvelope, { input: any }> {
    return async (parent, args, context, info) => {
      const commandEnvelope: CommandEnvelope = {
        requestID: context.requestID,
        currentUser: context.currentUser,
        typeName: commandClass.name,
        value: args.input,
        version: 1, // TODO: How to pass the version through GraphQL?
      }
      await this.commandsDispatcher.dispatchCommand(commandEnvelope)
      return true
    }
  }

  public subscriptionResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLRequestEnvelope, Record<string, ReadModelPropertyFilter>> {
    return (parent, args, context, info) => {
      const readModelEnvelope: ReadModelRequestEnvelope = {
        requestID: context.requestID,
        currentUser: context.currentUser,
        typeName: readModelClass.name,
        filters: args,
        version: 1, // TODO: How to pass the version through GraphQL?
      }
      return this.readModelsDispatcher.fetch(readModelEnvelope)
    }
  }
}
