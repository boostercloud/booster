package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-events-generator.ts}: the {@code eventsByEntity}/{@code eventsByType} queries and their types.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.2.4).
 */
public class GraphqlQueryEventsGenerator {

  /**
   * @param config the configuration
   * @param byIDResolverBuilder unused, as in TypeScript
   * @param eventsResolver the events resolver
   */
  public GraphqlQueryEventsGenerator(BoosterConfig config, ResolverBuilder byIDResolverBuilder, DataFetcher<Object> eventsResolver) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the generated fields by name
   */
  public Map<String, GraphQLFieldDefinition> generateEventQueries() {
    throw new UnsupportedOperationException("not implemented yet: GraphqlQueryEventsGenerator#generateEventQueries");
  }
}
