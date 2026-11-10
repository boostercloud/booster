package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.Inflection;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.core.services.graphql.queryhelpers.GraphqlQueryFilterArgumentsBuilder;
import com.boostercloud.framework.core.services.graphql.queryhelpers.GraphqlQuerySortBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.Scalars;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-listed-generator.ts}: the paginated queries
 * {@code List<ReadModels>(filter: List<ReadModel>Filter, limit: Int, sortBy: <ReadModel>SortBy, afterCursor: JSON):
 * <ReadModel>Connection!} with {@code type <ReadModel>Connection { items: [<ReadModel>!]! count: Int! cursor: JSON }}.
 */
public class GraphqlQueryListedGenerator {

  private final List<Class<?>> readModels;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder filterResolverBuilder;
  private final BoosterConfig config;
  private final GraphqlQueryFilterArgumentsBuilder graphqlQueryFilterArgumentsBuilder;
  private final GraphqlQuerySortBuilder graphqlQuerySortBuilder;

  /**
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param filterResolverBuilder resolvers of list queries
   * @param generatedFiltersByTypeName shared cache of filter input types ({@code null} = a new one)
   * @param config the configuration
   */
  public GraphqlQueryListedGenerator(List<Class<?>> readModels, GraphQLTypeInformer typeInformer,
      ResolverBuilder filterResolverBuilder, Map<String, GraphQLInputObjectType> generatedFiltersByTypeName,
      BoosterConfig config) {
    this.readModels = readModels;
    this.typeInformer = typeInformer;
    this.filterResolverBuilder = filterResolverBuilder;
    this.config = config;
    this.graphqlQueryFilterArgumentsBuilder = new GraphqlQueryFilterArgumentsBuilder(typeInformer,
        generatedFiltersByTypeName, config);
    this.graphqlQuerySortBuilder = new GraphqlQuerySortBuilder(typeInformer, config);
  }

  /**
   * {@code generateListedQueries()}.
   *
   * @return the generated fields by name, in read model order
   */
  public Map<String, GraphQLFieldConfig> generateListedQueries() {
    Map<String, GraphQLFieldConfig> queries = new LinkedHashMap<>();
    for (Class<?> readModel : readModels) {
      String readModelName = readModel.getSimpleName();
      List<String> excludeProp = config.nonExposedGraphQLMetadataKey.get(readModelName);
      GraphQLOutputType graphQLType = Common.asOutputType(typeInformer.generateGraphQLTypeForClass(readModel,
          excludeProp));
      GraphQLObjectType connection = GraphQLObjectType.newObject()
          .name(readModelName + "Connection")
          .field(field -> field.name("items")
              .type(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(graphQLType)))))
          .field(field -> field.name("count").type(GraphQLNonNull.nonNull(Scalars.GraphQLInt)))
          .field(field -> field.name("cursor").type(Common.jsonScalar()))
          .build();
      queries.put("List" + Inflection.pluralize(readModelName), GraphQLFieldConfig.of(
          GraphQLNonNull.nonNull(connection),
          generateListedQueriesFields(readModelName, readModel, excludeProp),
          filterResolverBuilder.build(readModel)));
    }
    return queries;
  }

  private Map<String, GraphQLInputType> generateListedQueriesFields(String name, Class<?> type,
      List<String> excludeProps) {
    Map<String, GraphQLInputType> filterArguments = graphqlQueryFilterArgumentsBuilder.generateFilterArguments(type,
        excludeProps);
    String filterName = "List" + type.getSimpleName() + "Filter";
    GraphQLTypeReference self = GraphQLTypeReference.typeRef(filterName);
    Map<String, GraphQLInputType> filterFields = new LinkedHashMap<>(filterArguments);
    filterFields.put("and", GraphQLList.list(self));
    filterFields.put("or", GraphQLList.list(self));
    filterFields.put("not", self);
    GraphQLInputObjectType filter = Common.inputObjectType(filterName, filterFields);

    Map<String, GraphQLInputType> sortArguments = graphqlQuerySortBuilder.generateSortArguments(type, excludeProps);
    GraphQLInputObjectType sort = Common.inputObjectType(name + "SortBy", sortArguments);

    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    args.put("filter", filter);
    args.put("limit", Scalars.GraphQLInt);
    args.put("sortBy", sort);
    args.put("afterCursor", Common.jsonScalar());
    return args;
  }
}
