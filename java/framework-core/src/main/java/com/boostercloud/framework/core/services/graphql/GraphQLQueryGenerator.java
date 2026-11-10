package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/graphql-query-generator.ts}: builds the root {@code Query} type from the five query
 * sub-generators.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.2).
 */
public class GraphQLQueryGenerator {

  /**
   * @param config the configuration
   * @param readModels the read model classes
   * @param targetTypes the query handlers
   * @param typeInformer the type informer
   * @param byIDResolverBuilder resolvers of by-id queries
   * @param queryResolverBuilder resolvers of user queries
   * @param filterResolverBuilder resolvers of filter/list queries
   * @param eventsResolver resolver of event queries
   * @param generatedFiltersByTypeName shared cache of filter input types
   */
  public GraphQLQueryGenerator(BoosterConfig config, List<Class<?>> readModels, Map<String, TargetTypeMetadata> targetTypes,
      GraphQLTypeInformer typeInformer, ResolverBuilder byIDResolverBuilder, ResolverBuilder queryResolverBuilder,
      ResolverBuilder filterResolverBuilder, DataFetcher<Object> eventsResolver,
      Map<String, GraphQLInputObjectType> generatedFiltersByTypeName) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the {@code Query} type
   */
  public GraphQLObjectType generate() {
    throw new UnsupportedOperationException("not implemented yet: GraphQLQueryGenerator#generate");
  }
}
