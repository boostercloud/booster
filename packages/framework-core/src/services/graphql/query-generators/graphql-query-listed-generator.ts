import {
  GraphQLFieldConfigArgumentMap,
  GraphQLFieldConfigMap,
  GraphQLInputObjectType,
  GraphQLInt,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
} from 'graphql'
import { GraphQLResolverContext, ResolverBuilder } from '../common'
import * as inflected from 'inflected'
import { GraphQLJSONObject } from 'graphql-type-json'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQuerySortBuilder } from '../query-helpers/graphql-query-sort-builder'
import { GraphqlQueryFilterArgumentsBuilder } from '../query-helpers/graphql-query-filter-arguments-builder'
import { AnyClass } from '@boostercloud/framework-types'

export class GraphqlQueryListedGenerator {
  private graphqlQueryFilterArgumentsBuilder: GraphqlQueryFilterArgumentsBuilder
  private graphqlQuerySortBuilder: GraphqlQuerySortBuilder

  constructor(
    private readonly readModels: AnyClass[],
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
    for (const readModel of this.readModels) {
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel)
      queries[`List${inflected.pluralize(readModel.name)}`] = {
        type: new GraphQLNonNull(
          new GraphQLObjectType({
            name: `${readModel.name}Connection`,
            fields: {
              items: { type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLType))) },
              count: { type: new GraphQLNonNull(GraphQLInt) },
              cursor: { type: GraphQLJSONObject },
            },
          })
        ),
        args: this.generateListedQueriesFields(readModel.name, readModel),
        resolve: this.filterResolverBuilder(readModel),
      }
    }
    return queries
  }

  private generateListedQueriesFields(name: string, type: AnyClass): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.graphqlQueryFilterArgumentsBuilder.generateFilterArguments(type)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `List${type.name}Filter`,
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
