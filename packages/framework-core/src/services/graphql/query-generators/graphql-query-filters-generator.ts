import { GraphQLFieldConfigMap, GraphQLInputObjectType, GraphQLList } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder, TargetTypesMap } from '../common'
import * as inflected from 'inflected'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQueryFilterFieldsBuilder } from '../query-helpers/graphql-query-filter-fields-builder'

export class GraphqlQueryFiltersGenerator {
  private graphqlQueryFilterFieldsBuilder: GraphqlQueryFilterFieldsBuilder

  constructor(
    private readonly readModelsMetadata: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly filterResolverBuilder: ResolverBuilder,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  ) {
    this.graphqlQueryFilterFieldsBuilder = new GraphqlQueryFilterFieldsBuilder(typeInformer, generatedFiltersByTypeName)
  }

  public generateFilterQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const name in this.readModelsMetadata) {
      const type = this.readModelsMetadata[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      queries[inflected.pluralize(name)] = {
        type: new GraphQLList(graphQLType),
        args: this.graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(name, type),
        resolve: this.filterResolverBuilder(type.class),
        deprecationReason: 'Method is deprecated. Use List* methods',
      }
    }
    return queries
  }
}
