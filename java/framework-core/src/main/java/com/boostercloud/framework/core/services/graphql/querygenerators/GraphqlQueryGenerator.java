package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.core.services.graphql.TargetTypeMetadata;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLFieldDefinition;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-generator.ts}: the user-defined {@code @Query} handlers as {@code Query} fields.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.2).
 */
public class GraphqlQueryGenerator {

  /**
   * @param targetTypes the query handlers
   * @param typeInformer the type informer
   * @param queryResolverBuilder resolvers of queries
   * @param config the configuration
   */
  public GraphqlQueryGenerator(Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer, ResolverBuilder queryResolverBuilder, BoosterConfig config) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the generated fields by name
   */
  public Map<String, GraphQLFieldDefinition> generateQueries() {
    throw new UnsupportedOperationException("not implemented yet: GraphqlQueryGenerator#generateQueries");
  }
}
