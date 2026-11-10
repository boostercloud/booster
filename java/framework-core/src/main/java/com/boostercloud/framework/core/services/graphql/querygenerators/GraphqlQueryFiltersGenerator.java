package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.Inflection;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.core.services.graphql.queryhelpers.GraphqlQueryFilterFieldsBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-filters-generator.ts}: the deprecated plural filter
 * queries {@code <ReadModels>(filter: <ReadModel>Filter): [<ReadModel>!]!}.
 */
public class GraphqlQueryFiltersGenerator {

  /** The deprecation reason of the plural queries. */
  public static final String DEPRECATION_REASON = "Method is deprecated. Use List* methods";

  private final List<Class<?>> readModels;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder filterResolverBuilder;
  private final BoosterConfig config;
  private final GraphqlQueryFilterFieldsBuilder graphqlQueryFilterFieldsBuilder;

  /**
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param filterResolverBuilder resolvers of filter queries
   * @param generatedFiltersByTypeName shared cache of filter input types ({@code null} = a new one)
   * @param config the configuration
   */
  public GraphqlQueryFiltersGenerator(List<Class<?>> readModels, GraphQLTypeInformer typeInformer,
      ResolverBuilder filterResolverBuilder, Map<String, GraphQLInputObjectType> generatedFiltersByTypeName,
      BoosterConfig config) {
    this.readModels = readModels;
    this.typeInformer = typeInformer;
    this.filterResolverBuilder = filterResolverBuilder;
    this.config = config;
    this.graphqlQueryFilterFieldsBuilder = new GraphqlQueryFilterFieldsBuilder(typeInformer,
        generatedFiltersByTypeName, config);
  }

  /**
   * {@code generateFilterQueries()}.
   *
   * @return the generated fields by name, in read model order
   */
  public Map<String, GraphQLFieldConfig> generateFilterQueries() {
    Map<String, GraphQLFieldConfig> queries = new LinkedHashMap<>();
    for (Class<?> readModel : readModels) {
      List<String> excludeProp = config.nonExposedGraphQLMetadataKey.get(readModel.getSimpleName());
      GraphQLOutputType graphQLType = Common.asOutputType(typeInformer.generateGraphQLTypeForClass(readModel,
          excludeProp));
      queries.put(Inflection.pluralize(readModel.getSimpleName()), new GraphQLFieldConfig(
          GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(graphQLType))),
          graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(readModel.getSimpleName(), readModel,
              excludeProp),
          filterResolverBuilder.build(readModel),
          null,
          DEPRECATION_REASON));
    }
    return queries;
  }
}
