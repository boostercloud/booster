package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;

/**
 * Port of {@code services/graphql/graphql-generator.ts}: the process-wide builder of the {@link GraphQLSchema} and
 * the resolver factories (read model search / by id, events, commands, queries, subscriptions).
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.1, §3.9).
 */
public final class GraphQLGenerator {

  private GraphQLGenerator() {}

  /**
   * {@code generateSchema(config)} (cached singleton). Implemented by stage core-3.
   *
   * @param config the configuration
   * @return the schema
   */
  public static GraphQLSchema generateSchema(BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#generateSchema");
  }

  /**
   * {@code readModelResolverBuilder(readModelClass)}. Implemented by stage core-3.
   *
   * @param readModelClass the read model class
   * @return the filter/list resolver
   */
  public static DataFetcher<Object> readModelResolverBuilder(Class<?> readModelClass) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#readModelResolverBuilder");
  }

  /**
   * {@code readModelByIDResolverBuilder(config, readModelClass)}. Implemented by stage core-3.
   *
   * @param config the configuration
   * @param readModelClass the read model class
   * @return the by-id resolver
   */
  public static DataFetcher<Object> readModelByIDResolverBuilder(BoosterConfig config, Class<?> readModelClass) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#readModelByIDResolverBuilder");
  }

  /**
   * {@code eventResolver}: the resolver of {@code eventsByEntity}/{@code eventsByType}. Implemented by stage core-3.
   *
   * @return the resolver
   */
  public static DataFetcher<Object> eventResolver() {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#eventResolver");
  }

  /**
   * {@code commandResolverBuilder(commandClass)}. Implemented by stage core-3.
   *
   * @param commandClass the command class
   * @return the mutation resolver
   */
  public static DataFetcher<Object> commandResolverBuilder(Class<?> commandClass) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#commandResolverBuilder");
  }

  /**
   * {@code queriesResolverBuilder(queryClass)}. Implemented by stage core-3.
   *
   * @param queryClass the query class
   * @return the query resolver
   */
  public static DataFetcher<Object> queriesResolverBuilder(Class<?> queryClass) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#queriesResolverBuilder");
  }

  /**
   * {@code subscriptionByIDResolverBuilder(config, readModelClass)}. Implemented by stage core-3.
   *
   * @param config the configuration
   * @param readModelClass the read model class
   * @return the by-id subscription resolver
   */
  public static DataFetcher<Object> subscriptionByIDResolverBuilder(BoosterConfig config, Class<?> readModelClass) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#subscriptionByIDResolverBuilder");
  }

  /**
   * {@code subscriptionResolverBuilder(config, readModelClass)}. Implemented by stage core-3.
   *
   * @param config the configuration
   * @param readModelClass the read model class
   * @return the filtered subscription resolver
   */
  public static DataFetcher<Object> subscriptionResolverBuilder(BoosterConfig config, Class<?> readModelClass) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLGenerator#subscriptionResolverBuilder");
  }
}
