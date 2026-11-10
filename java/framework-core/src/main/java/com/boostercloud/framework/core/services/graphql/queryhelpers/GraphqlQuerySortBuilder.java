package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLArgument;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-helpers/graphql-query-sort-builder.ts}: per-property sort input types.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.4).
 */
public class GraphqlQuerySortBuilder {

  /**
   * @param typeInformer the type informer
   * @param config the configuration
   */
  public GraphqlQuerySortBuilder(GraphQLTypeInformer typeInformer, BoosterConfig config) {}

  /**
   * Implemented by stage core-3.
   *
   * @param type the class
   * @param excludeProps properties to leave out
   * @return the generated arguments by name
   */
  public Map<String, GraphQLArgument> generateSortArguments(Class<?> type, List<String> excludeProps) {
    throw new UnsupportedOperationException("not implemented yet: GraphqlQuerySortBuilder#generateSortArguments");
  }
}
