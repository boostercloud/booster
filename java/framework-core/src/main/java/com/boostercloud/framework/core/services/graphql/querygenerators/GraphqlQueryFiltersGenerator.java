package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectType;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-filters-generator.ts}: the deprecated plural filter queries {@code <ReadModels>(filter)}.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.2.2).
 */
public class GraphqlQueryFiltersGenerator {

  /**
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param filterResolverBuilder resolvers of filter queries
   * @param generatedFiltersByTypeName shared cache of filter input types
   * @param config the configuration
   */
  public GraphqlQueryFiltersGenerator(List<Class<?>> readModels, GraphQLTypeInformer typeInformer, ResolverBuilder filterResolverBuilder, Map<String, GraphQLInputObjectType> generatedFiltersByTypeName, BoosterConfig config) {}

  /**
   * Implemented by stage core-3.
   *
   * @return the generated fields by name
   */
  public Map<String, GraphQLFieldDefinition> generateFilterQueries() {
    throw new UnsupportedOperationException("not implemented yet: GraphqlQueryFiltersGenerator#generateFilterQueries");
  }
}
