import { AnyClass, BoosterConfig, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLSchema } from 'graphql'
import { Booster } from '../../booster'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'
import { GraphQLMutationGenerator } from './graphql-mutations-generator'
import { BoosterCommandDispatcher } from '../../booster-command-dispatcher'
import { BoosterReadModelFetcher } from '../../booster-read-model-fetcher'
import { CommandEnvelope } from '@boostercloud/framework-types/dist'

export class GraphQLGenerator {
  private queryGenerator: GraphQLQueryGenerator
  private mutationGenerator: GraphQLMutationGenerator
  private readonly typeInformer: GraphQLTypeInformer

  public constructor(
    config: BoosterConfig,
    private commandsDispatcher: BoosterCommandDispatcher,
    private readModelsDispatcher: BoosterReadModelFetcher
  ) {
    console.log(this.readModelsDispatcher)
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
  }

  public generateSchema(): GraphQLSchema {
    return new GraphQLSchema({
      query: this.queryGenerator.generate(),
      mutation: this.mutationGenerator.generate(),
    })
  }

  public readModelFilterResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLRequestEnvelope, any> {
    return (parent, args, context, info) => {
      //this.readModelsDispatcher.fetch()
      const searcher = Booster.readModel(readModelClass)
      for (const propName in args) {
        const filter = args[propName]
        searcher.filter(propName, filter.operation, ...filter.values)
      }
      return searcher.search()
    }
  }

  public readModelByIDResolverBuilder(
    readModelClass: AnyClass
  ): GraphQLFieldResolver<any, GraphQLRequestEnvelope, any> {
    return (parent, args, context, info) => {
      const searcher = Booster.readModel(readModelClass)
      searcher.filter('id', '=', args.id)
      return searcher.searchOne()
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
}
