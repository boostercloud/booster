package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.Scalars;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLTypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-helpers/graphql-query-filter-fields-builder.ts}: the {@code filter} argument
 * of the deprecated plural queries and of the filtered subscriptions,
 * {@code input <name>Filter { <property filters...> and: [<name>Filter] or: [<name>Filter] not: <name>Filter
 * isDefined: Boolean }}.
 */
public class GraphqlQueryFilterFieldsBuilder {

  private final GraphqlQueryFilterArgumentsBuilder graphqlQueryFilterArgumentsBuilder;

  /**
   * @param typeInformer the type informer
   * @param generatedFiltersByTypeName shared cache of filter input types ({@code null} = a new one)
   * @param config the configuration
   */
  public GraphqlQueryFilterFieldsBuilder(GraphQLTypeInformer typeInformer,
      Map<String, GraphQLInputObjectType> generatedFiltersByTypeName, BoosterConfig config) {
    this.graphqlQueryFilterArgumentsBuilder = new GraphqlQueryFilterArgumentsBuilder(typeInformer,
        generatedFiltersByTypeName, config);
  }

  /**
   * {@code generateFilterQueriesFields(name, type, excludeProps)}.
   *
   * @param name the prefix of the filter type name ({@code <ReadModel>} or {@code <ReadModel>Subscription})
   * @param type the class
   * @param excludeProps properties to leave out, or {@code null}
   * @return the {@code filter} argument
   */
  public Map<String, GraphQLInputType> generateFilterQueriesFields(String name, Class<?> type,
      List<String> excludeProps) {
    Map<String, GraphQLInputType> filterArguments = graphqlQueryFilterArgumentsBuilder.generateFilterArguments(type,
        excludeProps);
    String filterName = name + "Filter";
    GraphQLTypeReference self = GraphQLTypeReference.typeRef(filterName);
    Map<String, GraphQLInputType> fields = new LinkedHashMap<>(filterArguments);
    fields.put("and", GraphQLList.list(self));
    fields.put("or", GraphQLList.list(self));
    fields.put("not", self);
    fields.put("isDefined", Scalars.GraphQLBoolean);
    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    args.put("filter", Common.inputObjectType(filterName, fields));
    return args;
  }
}
