package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldResolver;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.Scalars;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-events-generator.ts}: the {@code eventsByEntity} and
 * {@code eventsByType} queries, returning {@code [EventQueryResponse]}. The {@code EntityType} enum lists the
 * entities and the {@code EventType} enum the events that have a reducer.
 */
public class GraphqlQueryEventsGenerator {

  private final BoosterConfig config;
  private final GraphQLFieldResolver eventsResolver;

  /**
   * @param config the configuration
   * @param byIDResolverBuilder unused, as in TypeScript
   * @param eventsResolver the events resolver
   */
  public GraphqlQueryEventsGenerator(BoosterConfig config, ResolverBuilder byIDResolverBuilder,
      GraphQLFieldResolver eventsResolver) {
    this.config = config;
    this.eventsResolver = eventsResolver;
  }

  /**
   * {@code generateEventQueries()}.
   *
   * @return the {@code eventsByEntity} and {@code eventsByType} fields
   */
  public Map<String, GraphQLFieldConfig> generateEventQueries() {
    GraphQLOutputType eventQueryResponse = buildEventQueryResponse();

    Map<String, GraphQLInputType> byEntityArgs = new LinkedHashMap<>();
    byEntityArgs.put("entity", GraphQLNonNull.nonNull(
        Common.buildGraphqlSimpleEnumFor("EntityType", new ArrayList<>(config.entities.keySet()))));
    byEntityArgs.put("entityID", Scalars.GraphQLID);
    byEntityArgs.put("from", Scalars.GraphQLString);
    byEntityArgs.put("to", Scalars.GraphQLString);
    byEntityArgs.put("limit", Scalars.GraphQLInt);

    Map<String, GraphQLInputType> byTypeArgs = new LinkedHashMap<>();
    byTypeArgs.put("type", GraphQLNonNull.nonNull(
        Common.buildGraphqlSimpleEnumFor("EventType", new ArrayList<>(config.reducers.keySet()))));
    byTypeArgs.put("from", Scalars.GraphQLString);
    byTypeArgs.put("to", Scalars.GraphQLString);
    byTypeArgs.put("limit", Scalars.GraphQLInt);

    Map<String, GraphQLFieldConfig> queries = new LinkedHashMap<>();
    queries.put("eventsByEntity", GraphQLFieldConfig.of(eventQueryResponse, byEntityArgs, eventsResolver));
    queries.put("eventsByType", GraphQLFieldConfig.of(eventQueryResponse, byTypeArgs, eventsResolver));
    return queries;
  }

  private static GraphQLOutputType buildEventQueryResponse() {
    GraphQLObjectType user = GraphQLObjectType.newObject()
        .name("User")
        .field(field -> field.name("id").type(Scalars.GraphQLString))
        .field(field -> field.name("username").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
        .field(field -> field.name("roles").type(GraphQLList.list(GraphQLNonNull.nonNull(Scalars.GraphQLString))))
        .build();
    return GraphQLList.list(GraphQLObjectType.newObject()
        .name("EventQueryResponse")
        .field(field -> field.name("type").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
        .field(field -> field.name("entity").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
        .field(field -> field.name("entityID").type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
        .field(field -> field.name("requestID").type(GraphQLNonNull.nonNull(Scalars.GraphQLID)))
        .field(field -> field.name("user").type(user))
        .field(field -> field.name("createdAt").type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
        .field(field -> field.name("value").type(GraphQLNonNull.nonNull(Common.jsonScalar())))
        .field(field -> field.name("deletedAt").type(Scalars.GraphQLString))
        .build());
  }
}
