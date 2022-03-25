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
import { AnyClass } from '@boostercloud/framework-types'
import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphQLJSONObject } from 'graphql-type-json'
import { GraphqlQuerySortBuilder } from './builders/graphql-query-sort-builder'
import { GraphqlQueryFilterArgumentsBuilder } from './builders/graphql-query-filter-arguments-builder'

export class GraphqlQueryListedGenerator {
  private graphqlQueryFilterArgumentsBuilder: GraphqlQueryFilterArgumentsBuilder
  private graphqlQuerySortBuilder: GraphqlQuerySortBuilder

  public constructor(
    private readonly readModels: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly filterResolverBuilder: ResolverBuilder
  ) {
    this.graphqlQueryFilterArgumentsBuilder = new GraphqlQueryFilterArgumentsBuilder(typeInformer)
    this.graphqlQuerySortBuilder = new GraphqlQuerySortBuilder()
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
              cursor: { type: new GraphQLNonNull(GraphQLJSONObject) },
            },
          })
        ),
        args: this.generateListedQueriesFields(readModel),
        resolve: this.filterResolverBuilder(readModel),
      }
    }
    return queries
  }

  private generateListedQueriesFields(type: AnyClass): GraphQLFieldConfigArgumentMap {
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
    return {
      filter: { type: filter },
      limit: { type: GraphQLInt },
      sortBy: { type: sortArguments },
      afterCursor: { type: GraphQLJSONObject },
    }
  }
}
