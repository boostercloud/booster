package com.boostercloud.framework.core.services.graphql.querygenerators;

import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import graphql.Scalars;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-generators/graphql-query-by-keys-generator.ts}: one query per read model,
 * named after it: {@code <ReadModel>(id: ID!): <ReadModel>}, or, for read models with a sequence key,
 * {@code <ReadModel>(id: ID!, <sequenceKey>: ID): [<ReadModel>]}.
 */
public class GraphqlQueryByKeysGenerator {

  private final BoosterConfig config;
  private final List<Class<?>> readModels;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder byIDResolverBuilder;

  /**
   * @param config the configuration
   * @param readModels the read model classes
   * @param typeInformer the type informer
   * @param byIDResolverBuilder resolvers of by-id queries
   */
  public GraphqlQueryByKeysGenerator(BoosterConfig config, List<Class<?>> readModels, GraphQLTypeInformer typeInformer,
      ResolverBuilder byIDResolverBuilder) {
    this.config = config;
    this.readModels = readModels;
    this.typeInformer = typeInformer;
    this.byIDResolverBuilder = byIDResolverBuilder;
  }

  /**
   * {@code generateByKeysQueries()}.
   *
   * @return the generated fields by name, in read model order
   */
  public Map<String, GraphQLFieldConfig> generateByKeysQueries() {
    Map<String, GraphQLFieldConfig> queries = new LinkedHashMap<>();
    for (Class<?> readModel : readModels) {
      String readModelName = readModel.getSimpleName();
      String sequenceKeyName = config.readModelSequenceKeys.get(readModelName);
      if (Js.isTruthy(sequenceKeyName)) {
        queries.put(readModelName, generateByIdAndSequenceKeyQuery(readModel, sequenceKeyName));
      } else {
        queries.put(readModelName, generateByIdQuery(readModel));
      }
    }
    return queries;
  }

  /**
   * {@code generateByIdQuery(readModel)} (private in TypeScript).
   *
   * @param readModel the read model class
   * @return the {@code <ReadModel>(id: ID!)} field
   */
  GraphQLFieldConfig generateByIdQuery(Class<?> readModel) {
    GraphQLOutputType graphQLType = Common.asOutputType(typeInformer.generateGraphQLTypeForClass(readModel,
        config.nonExposedGraphQLMetadataKey.get(readModel.getSimpleName())));
    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    args.put("id", GraphQLNonNull.nonNull(Scalars.GraphQLID));
    return GraphQLFieldConfig.of(graphQLType, args, byIDResolverBuilder.build(readModel));
  }

  /**
   * {@code generateByIdAndSequenceKeyQuery(readModel, sequenceKeyName)} (private in TypeScript).
   *
   * @param readModel the read model class
   * @param sequenceKeyName the sequence key property
   * @return the {@code <ReadModel>(id: ID!, <sequenceKey>: ID)} field
   */
  GraphQLFieldConfig generateByIdAndSequenceKeyQuery(Class<?> readModel, String sequenceKeyName) {
    GraphQLOutputType graphQLType = Common.asOutputType(typeInformer.generateGraphQLTypeForClass(readModel,
        config.nonExposedGraphQLMetadataKey.get(readModel.getSimpleName())));
    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    args.put("id", GraphQLNonNull.nonNull(Scalars.GraphQLID));
    args.put(sequenceKeyName, Scalars.GraphQLID);
    return GraphQLFieldConfig.of(GraphQLList.list(graphQLType), args, byIDResolverBuilder.build(readModel));
  }
}
