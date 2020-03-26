import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLSchema } from 'graphql'
import { Booster } from '../../booster'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphQLQueryGenerator } from './graphql-query-generator'

export class GraphQLGenerator {
  private queryGenerator: GraphQLQueryGenerator
  private readonly typeInformer: GraphQLTypeInformer

  public constructor(config: BoosterConfig) {
    this.typeInformer = new GraphQLTypeInformer(config.readModels)
    this.queryGenerator = new GraphQLQueryGenerator(
      config.readModels,
      this.typeInformer,
      readModelByIDResolverBuilder,
      readModelFilterResolverBuilder
    )
  }

  public generateSchema(): GraphQLSchema {
    const query = this.queryGenerator.generate()
    return new GraphQLSchema({
      query,
    })
  }
}

function readModelFilterResolverBuilder(readModelClass: AnyClass): GraphQLFieldResolver<any, any, any> {
  return (parent, args, context, info) => {
    const searcher = Booster.readModel(readModelClass)
    for (const propName in args) {
      const filter = args[propName]
      searcher.filter(propName, filter.operation, ...filter.values)
    }
    return searcher.search()
  }
}

function readModelByIDResolverBuilder(readModelClass: AnyClass): GraphQLFieldResolver<any, any, any> {
  return (parent, args, context, info) => {
    const searcher = Booster.readModel(readModelClass)
    searcher.filter('id', '=', args.id)
    return searcher.searchOne()
  }
}
