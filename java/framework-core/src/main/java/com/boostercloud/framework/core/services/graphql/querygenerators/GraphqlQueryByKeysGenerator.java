package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLFieldDefinition;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-by-keys-generator.ts}: {@code <ReadModel>(id: ID!)} and {@code <ReadModel>(id: ID!, <sequenceKey>: ID)} queries.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.2.1).
 */
public class GraphqlQueryByKeysGenerator {

  /**
   * @param config the configuration
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param byIDResolverBuilder resolvers of by-id queries
   */
  public GraphqlQueryByKeysGenerator(BoosterConfig config, List<Class<?>> readModels, GraphQLTypeInformer typeInformer, ResolverBuilder byIDResolverBuilder) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the generated fields by name
   */
  public Map<String, GraphQLFieldDefinition> generateByKeysQueries() {
    throw new UnsupportedOperationException("not implemented yet: GraphqlQueryByKeysGenerator#generateByKeysQueries");
  }
}
