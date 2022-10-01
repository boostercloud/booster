import { GraphQLFieldConfigMap, GraphQLInputObjectType, GraphQLList, GraphQLNonNull } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder } from '../common'
import * as inflected from 'inflected'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQueryFilterFieldsBuilder } from '../query-helpers/graphql-query-filter-fields-builder'
import { AnyClass } from '@boostercloud/framework-types'

export class GraphqlQueryFiltersGenerator {
  private graphqlQueryFilterFieldsBuilder: GraphqlQueryFilterFieldsBuilder

  constructor(
    private readonly readModels: AnyClass[],
    private readonly queries: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly filterResolverBuilder: ResolverBuilder,
    private readonly queryResolverBuilder: ResolverBuilder,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  ) {
    this.graphqlQueryFilterFieldsBuilder = new GraphqlQueryFilterFieldsBuilder(typeInformer, generatedFiltersByTypeName)
  }

  public generateFilterQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModel of this.readModels) {
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel)
      queries[inflected.pluralize(readModel.name)] = {
        type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLType))),
        args: this.graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(readModel.name, readModel),
        resolve: this.filterResolverBuilder(readModel),
        deprecationReason: 'Method is deprecated. Use List* methods',
      }
    }
    for (const query of this.queries) {
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(query)
      queries[query.name] = {
        type: new GraphQLNonNull(graphQLType),
        args: this.graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(query.name, query),
        resolve: this.queryResolverBuilder(query),
      }
    }
    return queries
  }
}
