package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLObjectType;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code services/graphql/graphql-mutation-generator.ts}: the root {@code Mutation} type (one field per
 * command), absent when there are no commands.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.5).
 */
public class GraphQLMutationGenerator {

  /**
   * @param targetTypes the command handlers
   * @param typeInformer the type informer
   * @param mutationResolver resolvers of commands
   * @param config the configuration
   */
  public GraphQLMutationGenerator(Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer,
      ResolverBuilder mutationResolver, BoosterConfig config) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the {@code Mutation} type, empty without commands
   */
  public Optional<GraphQLObjectType> generate() {
    throw new UnsupportedOperationException("not implemented yet: GraphQLMutationGenerator#generate");
  }
}
