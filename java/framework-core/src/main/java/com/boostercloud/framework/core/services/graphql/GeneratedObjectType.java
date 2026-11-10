package com.boostercloud.framework.core.services.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLObjectType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An object type generated from field configurations, together with those configurations (graphql-js
 * {@code GraphQLObjectType} and its {@code toConfig()}): the root {@code Query}, {@code Mutation} and
 * {@code Subscription} types carry resolvers, which graphql-java needs in the code registry
 * ({@link #registerResolvers(GraphQLCodeRegistry.Builder)}).
 *
 * @param type the object type
 * @param fields the configuration of each field, in order
 */
public record GeneratedObjectType(GraphQLObjectType type, Map<String, GraphQLFieldConfig> fields) {

  /** Canonical constructor; keeps a read-only, ordered copy of {@code fields}. */
  public GeneratedObjectType {
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  /**
   * @param name the type name
   * @param fields the fields, in order
   * @return the generated type
   */
  public static GeneratedObjectType of(String name, Map<String, GraphQLFieldConfig> fields) {
    GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name(name);
    fields.forEach((fieldName, field) -> builder.field(field.toFieldDefinition(fieldName)));
    return new GeneratedObjectType(builder.build(), fields);
  }

  /**
   * @return the type name
   */
  public String name() {
    return type.getName();
  }

  /**
   * Registers the resolver of every field that has one.
   *
   * @param codeRegistry the schema's code registry
   */
  public void registerResolvers(GraphQLCodeRegistry.Builder codeRegistry) {
    fields.forEach((fieldName, field) -> {
      DataFetcher<?> dataFetcher = field.dataFetcher();
      if (dataFetcher != null) {
        codeRegistry.dataFetcher(FieldCoordinates.coordinates(type.getName(), fieldName), dataFetcher);
      }
    });
  }
}
