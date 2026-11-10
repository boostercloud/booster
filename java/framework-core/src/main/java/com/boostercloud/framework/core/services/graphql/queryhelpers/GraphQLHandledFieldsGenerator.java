package com.boostercloud.framework.core.services.graphql.queryhelpers;

import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.core.services.graphql.TargetTypeMetadata;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.metadata.MethodMetadata;
import com.boostercloud.metadata.TypeGroup;
import com.boostercloud.metadata.TypeMetadata;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code services/graphql/query-helpers/graphql-handled-fields-generator.ts}: the fields of commands
 * (mutations) and queries. Each field is named after the class; its type is the return type of the static
 * {@code handle} method (a future is unwrapped once); it has an {@code input: <Class>Input!} argument when the class
 * has properties.
 */
public class GraphQLHandledFieldsGenerator {

  private final Map<String, TargetTypeMetadata> targetTypes;
  private final GraphQLTypeInformer typeInformer;
  private final ResolverBuilder resolver;
  private final BoosterConfig config;

  /**
   * @param targetTypes the commands or queries, by name
   * @param typeInformer the type informer
   * @param resolver resolvers of the fields
   * @param config the configuration
   */
  public GraphQLHandledFieldsGenerator(Map<String, TargetTypeMetadata> targetTypes, GraphQLTypeInformer typeInformer,
      ResolverBuilder resolver, BoosterConfig config) {
    this.targetTypes = targetTypes;
    this.typeInformer = typeInformer;
    this.resolver = resolver;
    this.config = config;
  }

  /**
   * {@code generateFields()}: {@link #generateFields(boolean) generateFields(true)}.
   *
   * @return the fields by name
   */
  public Map<String, GraphQLFieldConfig> generateFields() {
    return generateFields(true);
  }

  /**
   * {@code generateFields(allowVoidReturn)}.
   *
   * @param allowVoidReturn whether a class without {@code handle} method gets the type {@code Boolean!} (commands)
   *     instead of failing (queries)
   * @return the fields by name, in the order of {@code targetTypes}
   * @throws IllegalStateException {@code Unexpected void return type for Query} when {@code allowVoidReturn} is false
   *     and a class has no {@code handle} method
   */
  public Map<String, GraphQLFieldConfig> generateFields(boolean allowVoidReturn) {
    Map<String, GraphQLFieldConfig> fields = new LinkedHashMap<>();
    targetTypes.forEach((name, metadata) -> {
      TypeMetadata handleMethodMetadata = getHandleMethodMetadata(metadata);
      TypeMetadata returnMetadata = getReturnMetadata(handleMethodMetadata, allowVoidReturn);
      GraphQLOutputType type = Common.asOutputType(typeInformer.getOrCreateGraphQLType(returnMetadata, false));
      GraphQLFieldConfig field = GraphQLFieldConfig.of(type, null, resolver.build(metadata.type()));
      Map<String, GraphQLInputType> input = generateInputForType(metadata);
      if (input != null) {
        field = field.withArgs(input);
      }
      fields.put(name, field);
    });
    return fields;
  }

  private Map<String, GraphQLInputType> generateInputForType(TargetTypeMetadata metadata) {
    if (metadata.properties().isEmpty()) {
      return null;
    }
    GraphQLInputType inputType = Common.asInputType(typeInformer.generateGraphQLTypeForClass(metadata.type(),
        config.nonExposedGraphQLMetadataKey.get(metadata.type().getSimpleName()), true));
    Map<String, GraphQLInputType> args = new LinkedHashMap<>();
    args.put("input", GraphQLNonNull.nonNull(inputType));
    return args;
  }

  private static TypeMetadata getHandleMethodMetadata(TargetTypeMetadata metadata) {
    TypeMetadata handleMethodMetadata = metadata.methods().stream()
        .filter(method -> method.name().equals("handle"))
        .findFirst()
        .map(MethodMetadata::typeInfo)
        .orElse(null);
    if (handleMethodMetadata != null && "Promise".equals(handleMethodMetadata.typeName())) {
      // If async function, return type is wrapped in a Promise
      handleMethodMetadata = handleMethodMetadata.parameters().getFirst();
    }
    return handleMethodMetadata;
  }

  private static TypeMetadata getReturnMetadata(TypeMetadata handleMethodMetadata, boolean allowVoidReturn) {
    if (handleMethodMetadata == null || handleMethodMetadata.name().equals("never")) {
      if (!allowVoidReturn) {
        throw new IllegalStateException("Unexpected void return type for Query");
      }
      return new TypeMetadata("Boolean", TypeGroup.BOOLEAN, "Boolean", List.of(), false, false, null, null, null);
    }
    return handleMethodMetadata;
  }
}
