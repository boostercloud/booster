package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.core.services.graphql.TargetTypeMetadata;
import com.boostercloud.framework.core.services.graphql.queryhelpers.GraphQLHandledFieldsGenerator;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-generator.ts}: the user-defined {@code @Query}
 * handlers as {@code Query} fields (a query without {@code handle} method fails the generation with
 * {@code Unexpected void return type for Query}).
 */
public class GraphqlQueryGenerator {

  private final Map<String, TargetTypeMetadata> targetTypes;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder queryResolverBuilder;
  private final BoosterConfig config;

  /**
   * @param targetTypes the query handlers, by name
   * @param typeInformer the type informer
   * @param queryResolverBuilder resolvers of queries
   * @param config the configuration
   */
  public GraphqlQueryGenerator(Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer,
      ResolverBuilder queryResolverBuilder, BoosterConfig config) {
    this.targetTypes = targetTypes;
    this.typeInformer = typeInformer;
    this.queryResolverBuilder = queryResolverBuilder;
    this.config = config;
  }

  /**
   * {@code generateQueries()}.
   *
   * @return the generated fields by name
   */
  public Map<String, GraphQLFieldConfig> generateQueries() {
    return new GraphQLHandledFieldsGenerator(targetTypes, typeInformer, queryResolverBuilder, config)
        .generateFields(false);
  }
}
