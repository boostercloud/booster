package com.boostercloud.framework.core.services.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The graphql-js {@code GraphQLFieldConfig}: the type, arguments, resolvers and deprecation of one field, before it
 * becomes part of an object type. graphql-java keeps resolvers in the schema's code registry instead of in the
 * field, so the generators return these configurations and {@link GeneratedObjectType} registers the resolvers.
 *
 * @param type the field type
 * @param args the arguments by name (their types), in declaration order; empty when the field has none
 * @param resolve the resolver, or {@code null} for the default property resolver
 * @param subscribe the subscription resolver (subscription root fields only), or {@code null}
 * @param deprecationReason the deprecation reason, or {@code null}
 */
public record GraphQLFieldConfig(
    GraphQLOutputType type,
    Map<String, GraphQLInputType> args,
    DataFetcher<?> resolve,
    DataFetcher<?> subscribe,
    String deprecationReason) {

  /** Canonical constructor; keeps a read-only, ordered copy of {@code args}. */
  public GraphQLFieldConfig {
    Objects.requireNonNull(type, "type");
    args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
  }

  /**
   * @param type the field type
   * @param args the arguments
   * @param resolve the resolver, or {@code null}
   * @return a field without subscription resolver nor deprecation
   */
  public static GraphQLFieldConfig of(GraphQLOutputType type, Map<String, GraphQLInputType> args,
      DataFetcher<?> resolve) {
    return new GraphQLFieldConfig(type, args, resolve, null, null);
  }

  /**
   * @param args the new arguments
   * @return a copy with the given arguments
   */
  public GraphQLFieldConfig withArgs(Map<String, GraphQLInputType> args) {
    return new GraphQLFieldConfig(type, args, resolve, subscribe, deprecationReason);
  }

  /**
   * @param name the field name
   * @return the graphql-java field definition (without resolvers)
   */
  public GraphQLFieldDefinition toFieldDefinition(String name) {
    GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition().name(name).type(type);
    args.forEach((argumentName, argumentType) ->
        builder.argument(GraphQLArgument.newArgument().name(argumentName).type(argumentType).build()));
    if (deprecationReason != null) {
      builder.deprecate(deprecationReason);
    }
    return builder.build();
  }

  /**
   * @return the resolver graphql-java calls for this field: the subscription resolver when there is one (graphql-java
   *     uses each published event as the field value, which is TypeScript's {@code resolve: (source) => source}),
   *     else the resolver
   */
  public DataFetcher<?> dataFetcher() {
    return subscribe != null ? subscribe : resolve;
  }
}
