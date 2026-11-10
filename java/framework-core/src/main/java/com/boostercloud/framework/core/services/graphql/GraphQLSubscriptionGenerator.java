package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.core.services.graphql.queryhelpers.GraphqlQueryFilterFieldsBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.Scalars;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code services/graphql/graphql-subcriptions-generator.ts}: the root {@code Subscription} type, absent when
 * there are no read models. Per read model: {@code <ReadModel>(id: ID!): <ReadModel>} and
 * {@code <ReadModels>(filter: <ReadModel>SubscriptionFilter): <ReadModel>}; each event published by the subscription
 * resolver is the value of the field (TypeScript {@code resolve: (source) => source}).
 */
public class GraphQLSubscriptionGenerator {

  private final List<Class<?>> readModels;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder byIDResolverBuilder;
  private final ResolverBuilder filterResolverBuilder;
  private final BoosterConfig config;
  private final GraphqlQueryFilterFieldsBuilder graphqlQueryFilterFieldsBuilder;

  /**
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param byIDResolverBuilder resolvers of by-id subscriptions
   * @param filterResolverBuilder resolvers of filtered subscriptions
   * @param generatedFiltersByTypeName shared cache of filter input types ({@code null} = a new one)
   * @param config the configuration
   */
  public GraphQLSubscriptionGenerator(List<Class<?>> readModels, GraphQLTypeInformer typeInformer,
      ResolverBuilder byIDResolverBuilder, ResolverBuilder filterResolverBuilder,
      Map<String, GraphQLInputObjectType> generatedFiltersByTypeName, BoosterConfig config) {
    this.readModels = readModels;
    this.typeInformer = typeInformer;
    this.byIDResolverBuilder = byIDResolverBuilder;
    this.filterResolverBuilder = filterResolverBuilder;
    this.config = config;
    this.graphqlQueryFilterFieldsBuilder = new GraphqlQueryFilterFieldsBuilder(typeInformer,
        generatedFiltersByTypeName, config);
  }

  /**
   * {@code generate()}.
   *
   * @return the {@code Subscription} type, empty without read models (TypeScript {@code undefined})
   */
  public Optional<GeneratedObjectType> generate() {
    Map<String, GraphQLFieldConfig> byIDSubscriptions = generateByIDSubscriptions();
    Map<String, GraphQLFieldConfig> filterSubscriptions = generateFilterSubscriptions();
    Map<String, GraphQLFieldConfig> fields = new LinkedHashMap<>(byIDSubscriptions);
    fields.putAll(filterSubscriptions);
    if (fields.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(GeneratedObjectType.of("Subscription", fields));
  }

  private Map<String, GraphQLFieldConfig> generateByIDSubscriptions() {
    Map<String, GraphQLFieldConfig> subscriptions = new LinkedHashMap<>();
    for (Class<?> readModel : readModels) {
      List<String> excludeProps = config.nonExposedGraphQLMetadataKey.get(readModel.getSimpleName());
      GraphQLOutputType graphQLType = Common.asOutputType(typeInformer.generateGraphQLTypeForClass(readModel,
          excludeProps));
      Map<String, GraphQLInputType> args = new LinkedHashMap<>();
      args.put("id", GraphQLNonNull.nonNull(Scalars.GraphQLID));
      subscriptions.put(readModel.getSimpleName(), new GraphQLFieldConfig(graphQLType, args,
          identityResolver(), byIDResolverBuilder.build(readModel), null));
    }
    return subscriptions;
  }

  private Map<String, GraphQLFieldConfig> generateFilterSubscriptions() {
    Map<String, GraphQLFieldConfig> subscriptions = new LinkedHashMap<>();
    for (Class<?> readModel : readModels) {
      List<String> excludeProps = config.nonExposedGraphQLMetadataKey.get(readModel.getSimpleName());
      GraphQLOutputType graphQLType = Common.asOutputType(typeInformer.generateGraphQLTypeForClass(readModel,
          excludeProps));
      subscriptions.put(Inflection.pluralize(readModel.getSimpleName()), new GraphQLFieldConfig(graphQLType,
          graphqlQueryFilterFieldsBuilder.generateFilterQueriesFields(readModel.getSimpleName() + "Subscription",
              readModel, excludeProps),
          identityResolver(), filterResolverBuilder.build(readModel), null));
    }
    return subscriptions;
  }

  /** {@code resolve: (source) => source}. */
  private static GraphQLFieldResolver identityResolver() {
    return (parent, args, context, info) -> parent;
  }
}
