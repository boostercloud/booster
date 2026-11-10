package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code services/graphql/graphql-subcriptions-generator.ts}: the root {@code Subscription} type (by id and
 * filtered, per read model), absent when there are no read models.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.8).
 */
public class GraphQLSubscriptionGenerator {

  /**
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param byIDResolverBuilder resolvers of by-id subscriptions
   * @param filterResolverBuilder resolvers of filtered subscriptions
   * @param generatedFiltersByTypeName shared cache of filter input types
   * @param config the configuration
   */
  public GraphQLSubscriptionGenerator(List<Class<?>> readModels, GraphQLTypeInformer typeInformer,
      ResolverBuilder byIDResolverBuilder, ResolverBuilder filterResolverBuilder,
      Map<String, GraphQLInputObjectType> generatedFiltersByTypeName, BoosterConfig config) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the {@code Subscription} type, empty without read models
   */
  public Optional<GraphQLObjectType> generate() {
    throw new UnsupportedOperationException("not implemented yet: GraphQLSubscriptionGenerator#generate");
  }
}
