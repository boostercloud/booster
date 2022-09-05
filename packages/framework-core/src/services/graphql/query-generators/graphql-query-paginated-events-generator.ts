import {
  GraphQLFieldConfigMap,
  GraphQLFieldResolver,
  GraphQLID,
  GraphQLInt,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLOutputType,
  GraphQLString,
} from 'graphql'
import { buildGraphqlSimpleEnumFor, GraphQLResolverContext, ResolverBuilder } from '../common'
import { GraphQLJSON } from 'graphql-scalars'
import { BoosterConfig } from '@boostercloud/framework-types'

export class GraphqlQueryPaginatedEventsGenerator {
  constructor(
    private readonly config: BoosterConfig,
    protected readonly byIDResolverBuilder: ResolverBuilder,
    private readonly eventsResolver: GraphQLFieldResolver<unknown, GraphQLResolverContext, any>
  ) {}

  public generatePaginatedEventQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const eventQueryResponse = GraphqlQueryPaginatedEventsGenerator.buildPaginatedEventQueryResponse()
    return {
      paginatedEventsByEntity: {
        type: eventQueryResponse,
        args: {
          entity: {
            type: new GraphQLNonNull(
              buildGraphqlSimpleEnumFor('PaginatedEntityType', Object.keys(this.config.entities))
            ),
          },
          entityID: { type: GraphQLID },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
          limit: { type: new GraphQLNonNull(GraphQLInt) },
          afterCursor: { type: GraphQLJSON },
        },
        resolve: this.eventsResolver,
      },
      paginatedEventsByType: {
        type: eventQueryResponse,
        args: {
          type: {
            type: new GraphQLNonNull(
              buildGraphqlSimpleEnumFor('PaginatedEventType', Object.keys(this.config.reducers))
            ),
          },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
          limit: { type: GraphQLInt },
          afterCursor: { type: GraphQLJSON },
        },
        resolve: this.eventsResolver,
      },
    }
  }

  private static buildPaginatedEventQueryResponse(): GraphQLOutputType {
    const graphQLType = new GraphQLObjectType({
      name: 'PaginatedEventQueryResponse',
      fields: {
        type: { type: new GraphQLNonNull(GraphQLString) },
        entity: { type: new GraphQLNonNull(GraphQLString) },
        entityID: { type: new GraphQLNonNull(GraphQLID) },
        requestID: { type: new GraphQLNonNull(GraphQLID) },
        user: {
          type: new GraphQLObjectType({
            name: 'PaginatedUser',
            fields: {
              id: { type: GraphQLString },
              username: { type: new GraphQLNonNull(GraphQLString) },
              roles: { type: new GraphQLList(new GraphQLNonNull(GraphQLString)) },
            },
          }),
        },
        createdAt: { type: new GraphQLNonNull(GraphQLString) },
        value: { type: new GraphQLNonNull(GraphQLJSON) },
      },
    })

    return new GraphQLNonNull(
      new GraphQLObjectType({
        name: 'PaginatedEventConnection',
        fields: {
          items: { type: new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(graphQLType))) },
          count: { type: new GraphQLNonNull(GraphQLInt) },
          cursor: { type: GraphQLJSON },
        },
      })
    )
  }
}
