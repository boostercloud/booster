import {
  GraphQLFieldConfigMap,
  GraphQLFieldResolver,
  GraphQLID,
  GraphQLList,
  GraphQLNonNull,
  GraphQLObjectType,
  GraphQLOutputType,
  GraphQLString,
} from 'graphql'
import { buildGraphqlSimpleEnumFor, GraphQLResolverContext } from '../common'
import { GraphQLJSONObject } from 'graphql-type-json'
import { BoosterConfig } from '@boostercloud/framework-types'

export class GraphqlQueryEventsGenerator {
  constructor(
    private readonly config: BoosterConfig,
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
        },
        resolve: this.eventsResolver,
      },
      eventsByType: {
        type: eventQueryResponse,
        args: {
          type: {
            type: new GraphQLNonNull(buildGraphqlSimpleEnumFor('EventType', Object.keys(this.config.reducers))),
          },
          from: { type: GraphQLString },
          to: { type: GraphQLString },
        },
        resolve: this.eventsResolver,
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
          value: { type: new GraphQLNonNull(GraphQLJSONObject) },
        },
      })
    )
  }
}
