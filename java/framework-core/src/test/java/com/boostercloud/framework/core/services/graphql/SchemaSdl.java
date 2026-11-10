package com.boostercloud.framework.core.services.graphql;

import graphql.schema.GraphQLSchema;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.idl.SchemaPrinter;

/** Prints generated schemas as SDL: types by name, fields in declaration order, {@code @deprecated} shown. */
final class SchemaSdl {

  private SchemaSdl() {}

  static String print(GraphQLSchema schema) {
    return new SchemaPrinter(SchemaPrinter.Options.defaultOptions()
        .includeDirectives(name -> name.equals("deprecated"))
        .includeDirectiveDefinitions(false)
        .includeScalarTypes(true)
        .setComparators(GraphqlTypeComparatorRegistry.AS_IS_REGISTRY))
        .print(schema);
  }
}
