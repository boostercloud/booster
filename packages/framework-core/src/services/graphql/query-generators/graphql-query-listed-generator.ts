import {
  GraphQLFieldConfigArgumentMap,
  GraphQLFieldConfigMap,
  GraphQLInputObjectType,
  GraphQLInt,
  GraphQLList,
  GraphQLObjectType,
} from 'graphql'
import { GraphQLResolverContext, ResolverBuilder, TargetTypeMetadata, TargetTypesMap } from '../common'
import * as inflected from 'inflected'
import { GraphQLJSONObject } from 'graphql-type-json'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQuerySortBuilder } from '../query-helpers/graphql-query-sort-builder'
import { GraphqlQueryFilterArgumentsBuilder } from '../query-helpers/graphql-query-filter-arguments-builder'

export class GraphqlQueryListedGenerator {
  private graphqlQueryFilterArgumentsBuilder: GraphqlQueryFilterArgumentsBuilder
  private graphqlQuerySortBuilder: GraphqlQuerySortBuilder

  constructor(
    private readonly readModelsMetadata: TargetTypesMap,
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly filterResolverBuilder: ResolverBuilder,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {}
  ) {
    this.graphqlQueryFilterArgumentsBuilder = new GraphqlQueryFilterArgumentsBuilder(
      typeInformer,
      generatedFiltersByTypeName
    )
    this.graphqlQuerySortBuilder = new GraphqlQuerySortBuilder(typeInformer)
  }

  public generateListedQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const name in this.readModelsMetadata) {
      const type = this.readModelsMetadata[name]
      const graphQLType = this.typeInformer.getGraphQLTypeFor(type.class)
      queries[`List${inflected.pluralize(name)}`] = {
        type: new GraphQLObjectType({
          name: `${name}Connection`,
          fields: {
            items: { type: new GraphQLList(graphQLType) },
            cursor: { type: GraphQLJSONObject },
          },
        }),
        args: this.generateListedQueriesFields(name, type),
        resolve: this.filterResolverBuilder(type.class),
      }
    }
    return queries
  }

  public generateListedQueriesFields(name: string, type: TargetTypeMetadata): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.graphqlQueryFilterArgumentsBuilder.generateFilterArguments(type)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `List${name}Filter`,
      fields: () => ({
        ...filterArguments,
        and: { type: new GraphQLList(filter) },
        or: { type: new GraphQLList(filter) },
        not: { type: filter },
      }),
    })
    const sortArguments = this.graphqlQuerySortBuilder.generateSortArguments(type)
    const sort: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `${name}SortBy`,
      fields: () => ({
        ...sortArguments,
      }),
    })
    return {
      filter: { type: filter },
      limit: { type: GraphQLInt },
      sortBy: { type: sort },
      afterCursor: { type: GraphQLJSONObject },
    }
  }
}
