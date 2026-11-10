package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.core.services.graphql.queryhelpers.GraphQLHandledFieldsGenerator;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code services/graphql/graphql-mutation-generator.ts}: the root {@code Mutation} type, one field per
 * command, absent when there are no commands.
 */
public class GraphQLMutationGenerator {

  private final Map<String, TargetTypeMetadata> targetTypes;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder mutationResolver;
  private final BoosterConfig config;

  /**
   * @param targetTypes the command handlers, by name
   * @param typeInformer the type informer
   * @param mutationResolver resolvers of commands
   * @param config the configuration
   */
  public GraphQLMutationGenerator(Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer,
      ResolverBuilder mutationResolver, BoosterConfig config) {
    this.targetTypes = targetTypes;
    this.typeInformer = typeInformer;
    this.mutationResolver = mutationResolver;
    this.config = config;
  }

  /**
   * {@code generate()}.
   *
   * @return the {@code Mutation} type, empty without commands (TypeScript {@code undefined})
   */
  public Optional<GeneratedObjectType> generate() {
    Map<String, GraphQLFieldConfig> mutations = new GraphQLHandledFieldsGenerator(targetTypes, typeInformer,
        mutationResolver, config).generateFields();
    if (mutations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(GeneratedObjectType.of("Mutation", mutations));
  }
}
