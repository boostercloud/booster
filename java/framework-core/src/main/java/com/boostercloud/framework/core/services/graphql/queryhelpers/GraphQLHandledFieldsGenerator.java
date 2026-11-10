package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.core.services.graphql.TargetTypeMetadata;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLFieldDefinition;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-helpers/graphql-handled-fields-generator.ts}: the fields of commands
 * (mutations) and queries, with the {@code input} argument and the return type of {@code handle}.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.6).
 */
public class GraphQLHandledFieldsGenerator {

  /**
   * @param targetTypes the commands or queries
   * @param typeInformer the type informer
   * @param resolver resolvers of the fields
   * @param config the configuration
   */
  public GraphQLHandledFieldsGenerator(Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer,
      ResolverBuilder resolver, BoosterConfig config) {}

  /**
   * {@code generateFields(allowVoidReturn = true)}. Implemented by stage core-3.
   *
   * @param allowVoidReturn whether {@code void} handlers return {@code Boolean}
   * @return the fields by name
   */
  public Map<String, GraphQLFieldDefinition> generateFields(boolean allowVoidReturn) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLHandledFieldsGenerator#generateFields");
  }
}
