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
import { GraphQLJSON } from 'graphql-scalars'

import { GraphQLTypeInformer } from '../graphql-type-informer'
import { GraphqlQuerySortBuilder } from '../query-helpers/graphql-query-sort-builder'
import { GraphqlQueryFilterArgumentsBuilder } from '../query-helpers/graphql-query-filter-arguments-builder'
import { AnyClass, BoosterConfig } from '@boostercloud/framework-types'

export class GraphqlQueryListedGenerator {
  private graphqlQueryFilterArgumentsBuilder: GraphqlQueryFilterArgumentsBuilder
  private graphqlQuerySortBuilder: GraphqlQuerySortBuilder

  constructor(
    private readonly readModels: AnyClass[],
    private readonly typeInformer: GraphQLTypeInformer,
    private readonly filterResolverBuilder: ResolverBuilder,
    protected generatedFiltersByTypeName: Record<string, GraphQLInputObjectType> = {},
    private readonly config: BoosterConfig
  ) {
    this.graphqlQueryFilterArgumentsBuilder = new GraphqlQueryFilterArgumentsBuilder(
      typeInformer,
      generatedFiltersByTypeName,
      config
    )
    this.graphqlQuerySortBuilder = new GraphqlQuerySortBuilder(typeInformer, config)
  }

  public generateListedQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const queries: GraphQLFieldConfigMap<unknown, GraphQLResolverContext> = {}
    for (const readModel of this.readModels) {
      const excludeProp = this.config.nonExposedGraphQLMetadataKey[readModel.name]
      const graphQLType = this.typeInformer.generateGraphQLTypeForClass(readModel, excludeProp)
      queries[`List${inflected.pluralize(readModel.name)}`] = {
        type: new GraphQLNonNull(
          new GraphQLObjectType({
            name: `${readModel.name}Connection`,
            fields: {
              items: { type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLType))) },
              count: { type: new GraphQLNonNull(GraphQLInt) },
              cursor: { type: GraphQLJSON },
            },
          })
        ),
        args: this.generateListedQueriesFields(readModel.name, readModel, excludeProp),
        resolve: this.filterResolverBuilder(readModel),
      }
    }
    return queries
  }

  private generateListedQueriesFields(
    name: string,
    type: AnyClass,
    excludeProps: Array<string>
  ): GraphQLFieldConfigArgumentMap {
    const filterArguments = this.graphqlQueryFilterArgumentsBuilder.generateFilterArguments(type, excludeProps)
    const filter: GraphQLInputObjectType = new GraphQLInputObjectType({
      name: `List${type.name}Filter`,
      fields: () => ({
        ...filterArguments,
        and: { type: new GraphQLList(filter) },
        or: { type: new GraphQLList(filter) },
        not: { type: filter },
      }),
    })
    const sortArguments = this.graphqlQuerySortBuilder.generateSortArguments(type, excludeProps)
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
      afterCursor: { type: GraphQLJSON },
    }
  }
}
