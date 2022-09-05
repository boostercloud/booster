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

export class GraphqlQueryEventsGenerator {
  constructor(
    private readonly config: BoosterConfig,
    protected readonly byIDResolverBuilder: ResolverBuilder,
    private readonly eventsResolver: GraphQLFieldResolver<unknown, GraphQLResolverContext, any>
  ) {}

  public generateEventQueries(): GraphQLFieldConfigMap<unknown, GraphQLResolverContext> {
    const eventQueryResponse = GraphqlQueryEventsGenerator.buildEventQueryResponse()
    return {
      eventsByEntity: {
        type: eventQueryResponse,
        args: {
          entity: {
            type: new GraphQLNonNull(buildGraphqlSimpleEnumFor('EntityType', Object.keys(this.config.entities))),
          },
          entityID: { type: GraphQLID },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
          limit: { type: GraphQLInt },
        },
        resolve: this.eventsResolver,
        deprecationReason: 'Method is deprecated. Use Paginated* methods',
      },
      eventsByType: {
        type: eventQueryResponse,
        args: {
          type: {
            type: new GraphQLNonNull(buildGraphqlSimpleEnumFor('EventType', Object.keys(this.config.reducers))),
          },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
          limit: { type: GraphQLInt },
        },
        resolve: this.eventsResolver,
        deprecationReason: 'Method is deprecated. Use Paginated* methods',
      },
    }
  }

  private static buildEventQueryResponse(): GraphQLOutputType {
    return new GraphQLList(
      new GraphQLObjectType({
        name: 'EventQueryResponse',
        fields: {
          type: { type: new GraphQLNonNull(GraphQLString) },
          entity: { type: new GraphQLNonNull(GraphQLString) },
          entityID: { type: new GraphQLNonNull(GraphQLID) },
          requestID: { type: new GraphQLNonNull(GraphQLID) },
          user: {
            type: new GraphQLObjectType({
              name: 'User',
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
    )
  }
}
