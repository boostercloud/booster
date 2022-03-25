import { GraphQLFieldConfigMap, GraphQLInputObjectType, GraphQLList, GraphQLNonNull } from 'graphql'
import { GraphQLResolverContext, ResolverBuilder } from '../common'
import * as inflected from 'inflected'
import { AnyClass } from '@boostercloud/framework-types'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQueryFilterFieldsBuilder } from '../query-helpers/graphql-query-filter-fields-builder'

export class GraphqlQueryFiltersGenerator {
  private graphqlQueryFilterFieldsBuilder: GraphqlQueryFilterFieldsBuilder

  public constructor(
    private readonly readModels: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly filterResolverBuilder: ResolverBuilder,
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
    return queries
  }
}
