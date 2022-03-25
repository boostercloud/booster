import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLObjectType } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphqlQueryByKeysGenerator } from './query-generators/graphql-query-by-keys-generator'
import { GraphqlQueryFiltersGenerator } from './query-generators/graphql-query-filters-generator'
import { GraphqlQueryListedGenerator } from './query-generators/graphql-query-listed-generator'
import { GraphqlQueryEventsGenerator } from './query-generators/graphql-query-events-generator'

export class GraphQLQueryGenerator {
  private generateFiltersQueries: GraphqlQueryFiltersGenerator
  private graphqlQueryByKeysGenerator: GraphqlQueryByKeysGenerator
  private graphqlQueryListGenerator: GraphqlQueryListedGenerator
  private graphqlQueryEventsGenerator: GraphqlQueryEventsGenerator

  public constructor(
    protected readonly config: BoosterConfig,
    protected readonly readModels: AnyClass[],
    protected readonly typeInformer: GraphQLTypeInformer,
    protected readonly byIDResolverBuilder: ResolverBuilder,
    protected readonly filterResolverBuilder: ResolverBuilder,
    protected readonly eventsResolver: GraphQLFieldResolver<unknown, GraphQLResolverContext, any>
  ) {
    this.generateFiltersQueries = new GraphqlQueryFiltersGenerator(readModels, typeInformer, filterResolverBuilder)
    this.graphqlQueryByKeysGenerator = new GraphqlQueryByKeysGenerator(
      config,
      readModels,
      byIDResolverBuilder,
      typeInformer
    )
    this.graphqlQueryListGenerator = new GraphqlQueryListedGenerator(readModels, typeInformer, filterResolverBuilder)
    this.graphqlQueryEventsGenerator = new GraphqlQueryEventsGenerator(config, eventsResolver)
  }

  public generate(): GraphQLObjectType {
    const byIDQueries = this.graphqlQueryByKeysGenerator.generateByKeysQueries()
    const filterQueries = this.generateFiltersQueries.generateFilterQueries()
    const listedQueries = this.graphqlQueryListGenerator.generateListedQueries()
    const eventQueries = this.graphqlQueryEventsGenerator.generateEventQueries()
    return new GraphQLObjectType({
      name: 'Query',
      fields: { ...byIDQueries, ...filterQueries, ...listedQueries, ...eventQueries },
    })
  }
}
