package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.core.services.graphql.querygenerators.GraphqlQueryByKeysGenerator;
import com.boostercloud.framework.core.services.graphql.querygenerators.GraphqlQueryEventsGenerator;
import com.boostercloud.framework.core.services.graphql.querygenerators.GraphqlQueryFiltersGenerator;
import com.boostercloud.framework.core.services.graphql.querygenerators.GraphqlQueryGenerator;
import com.boostercloud.framework.core.services.graphql.querygenerators.GraphqlQueryListedGenerator;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.schema.GraphQLInputObjectType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/graphql-query-generator.ts}: builds the root {@code Query} type from the five query
 * sub-generators. They run in the order by-keys, user queries, plural filters, paginated lists, events (which matters
 * for the shared type caches); the fields are in the order by-id, plural filters, lists, {@code eventsByEntity},
 * {@code eventsByType}, user queries, a later group replacing an earlier field of the same name in place (JavaScript
 * object spread).
 */
public class GraphQLQueryGenerator {

  private final GraphqlQueryByKeysGenerator graphqlQueryByKeysGenerator;
  private final GraphqlQueryGenerator graphqlQueryGenerator;
  private final GraphqlQueryFiltersGenerator graphqlQueryFiltersGenerator;
  private final GraphqlQueryListedGenerator graphqlQueryListedGenerator;
  private final GraphqlQueryEventsGenerator graphqlQueryEventsGenerator;

  /**
   * @param config the configuration
   * @param readModels the read model classes
   * @param targetTypes the query handlers, by name
   * @param typeInformer the type informer
   * @param byIDResolverBuilder resolvers of by-id queries
   * @param queryResolverBuilder resolvers of user queries
   * @param filterResolverBuilder resolvers of filter/list queries
   * @param eventsResolver resolver of event queries
   * @param generatedFiltersByTypeName shared cache of filter input types ({@code null} = a new one)
   */
  public GraphQLQueryGenerator(BoosterConfig config, List<Class<?>> readModels,
      Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer,
      ResolverBuilder byIDResolverBuilder, ResolverBuilder queryResolverBuilder, ResolverBuilder filterResolverBuilder,
      GraphQLFieldResolver eventsResolver, Map<String, GraphQLInputObjectType> generatedFiltersByTypeName) {
    Map<String, GraphQLInputObjectType> filters = generatedFiltersByTypeName == null ? new LinkedHashMap<>()
        : generatedFiltersByTypeName;
    this.graphqlQueryByKeysGenerator = new GraphqlQueryByKeysGenerator(config, readModels, typeInformer,
        byIDResolverBuilder);
    this.graphqlQueryGenerator = new GraphqlQueryGenerator(targetTypes, typeInformer, queryResolverBuilder, config);
    this.graphqlQueryFiltersGenerator = new GraphqlQueryFiltersGenerator(readModels, typeInformer,
        filterResolverBuilder, filters, config);
    this.graphqlQueryListedGenerator = new GraphqlQueryListedGenerator(readModels, typeInformer,
        filterResolverBuilder, filters, config);
    this.graphqlQueryEventsGenerator = new GraphqlQueryEventsGenerator(config, byIDResolverBuilder, eventsResolver);
  }

  /**
   * {@code generate()}.
   *
   * @return the {@code Query} type and the configuration of its fields
   */
  public GeneratedObjectType generate() {
    Map<String, GraphQLFieldConfig> byIDQueries = graphqlQueryByKeysGenerator.generateByKeysQueries();
    Map<String, GraphQLFieldConfig> queries = graphqlQueryGenerator.generateQueries();
    Map<String, GraphQLFieldConfig> filterQueries = graphqlQueryFiltersGenerator.generateFilterQueries();
    Map<String, GraphQLFieldConfig> listedQueries = graphqlQueryListedGenerator.generateListedQueries();
    Map<String, GraphQLFieldConfig> eventQueries = graphqlQueryEventsGenerator.generateEventQueries();
    Map<String, GraphQLFieldConfig> fields = new LinkedHashMap<>(byIDQueries);
    fields.putAll(filterQueries);
    fields.putAll(listedQueries);
    fields.putAll(eventQueries);
    fields.putAll(queries);
    return GeneratedObjectType.of("Query", fields);
  }
}
