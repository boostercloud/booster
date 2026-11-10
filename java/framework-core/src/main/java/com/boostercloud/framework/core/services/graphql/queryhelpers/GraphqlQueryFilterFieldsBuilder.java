package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectType;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-helpers/graphql-query-filter-fields-builder.ts}: the {@code filter} argument of filter queries and subscriptions.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.3).
 */
public class GraphqlQueryFilterFieldsBuilder {

  /**
   * @param typeInformer the type informer
   * @param generatedFiltersByTypeName shared cache of filter input types
   * @param config the configuration
   */
  public GraphqlQueryFilterFieldsBuilder(GraphQLTypeInformer typeInformer, Map<String, GraphQLInputObjectType> generatedFiltersByTypeName, BoosterConfig config) {}

  /**
   * Implemented by stage core-3.
   *
   * @param name the type name
   * @param type the class
   * @param excludeProps properties to leave out
   * @return the generated arguments by name
   */
  public Map<String, GraphQLArgument> generateFilterQueriesFields(String name, Class<?> type, List<String> excludeProps) {
    throw new UnsupportedOperationException("not implemented yet: GraphqlQueryFilterFieldsBuilder#generateFilterQueriesFields");
  }
}
