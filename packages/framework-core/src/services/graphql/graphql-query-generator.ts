import { BoosterConfig } from '@boostercloud/framework-types'
import { GraphQLFieldResolver, GraphQLInputObjectType, GraphQLObjectType } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder, TargetTypesMap } from './common'
import { GraphQLTypeInformer } from './graphql-type-informer'
import { GraphqlQueryByKeysGenerator } from './query-generators/graphql-query-by-keys-generator'
import { GraphqlQueryEventsGenerator } from './query-generators/graphql-query-events-generator'
import { GraphqlQueryFiltersGenerator } from './query-generators/graphql-query-filters-generator'
import { GraphqlQueryListedGenerator } from './query-generators/graphql-query-listed-generator'

export class GraphQLQueryGenerator {
  private graphqlQueryByKeysGenerator: GraphqlQueryByKeysGenerator
  private graphqlQueryFiltersGenerator: GraphqlQueryFiltersGenerator
  private graphqlQueryListedGenerator: GraphqlQueryListedGenerator
  private graphqlQueryEventsGenerator: GraphqlQueryEventsGenerator

  public constructor(
    protected readonly config: BoosterConfig,
    protected readonly readModelsMetadata: TargetTypesMap,
    protected readonly typeInformer: GraphQLTypeInformer,
    protected readonly byIDResolverBuilder: ResolverBuilder,
    protected readonly filterResolverBuilder: ResolverBuilder,
    protected readonly eventsResolver: GraphQLFieldResolver<unknown, GraphQLResolverContext, any>,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  ) {
    this.graphqlQueryByKeysGenerator = new GraphqlQueryByKeysGenerator(
      config,
      readModelsMetadata,
      typeInformer,
      byIDResolverBuilder
    )
    this.graphqlQueryFiltersGenerator = new GraphqlQueryFiltersGenerator(
      readModelsMetadata,
      typeInformer,
      filterResolverBuilder,
      generatedFiltersByTypeName
    )
    this.graphqlQueryListedGenerator = new GraphqlQueryListedGenerator(
      readModelsMetadata,
      typeInformer,
      filterResolverBuilder,
      generatedFiltersByTypeName
    )
    this.graphqlQueryEventsGenerator = new GraphqlQueryEventsGenerator(config, byIDResolverBuilder, eventsResolver)
  }

  public generate(): GraphQLObjectType {
    const byIDQueries = this.graphqlQueryByKeysGenerator.generateByKeysQueries()
    const filterQueries = this.graphqlQueryFiltersGenerator.generateFilterQueries()
    const listedQueries = this.graphqlQueryListedGenerator.generateListedQueries()
    const eventQueries = this.graphqlQueryEventsGenerator.generateEventQueries()
    return new GraphQLObjectType({
      name: 'Query',
      fields: { ...byIDQueries, ...filterQueries, ...listedQueries, ...eventQueries },
    })
  }
}
