package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.Logger;
import com.boostercloud.metadata.TypeMetadata;
import graphql.schema.GraphQLType;
import java.util.List;

/**
 * Port of {@code services/graphql/graphql-type-informer.ts}: maps class/type metadata to GraphQL output and input
 * types, with a by-name cache.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.7).
 */
public class GraphQLTypeInformer {

  private final Logger logger;

  /**
   * @param logger the logger
   */
  public GraphQLTypeInformer(Logger logger) {
    this.logger = logger;
  }

  /**
   * @return the logger
   */
  public Logger logger() {
    return logger;
  }

  /**
   * {@code generateGraphQLTypeForClass(type, excludeProps, inputType)}. Implemented by stage core-3.
   *
   * @param type the class
   * @param excludeProps properties to leave out
   * @param inputType whether an input type is generated
   * @return the type
   */
  public GraphQLType generateGraphQLTypeForClass(Class<?> type, List<String> excludeProps, boolean inputType) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLTypeInformer#generateGraphQLTypeForClass");
  }

  /**
   * {@code getOrCreateGraphQLType(typeMetadata, inputType)}. Implemented by stage core-3.
   *
   * @param typeMetadata the type
   * @param inputType whether an input type is generated
   * @return the type
   */
  public GraphQLType getOrCreateGraphQLType(TypeMetadata typeMetadata, boolean inputType) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLTypeInformer#getOrCreateGraphQLType");
  }
}
