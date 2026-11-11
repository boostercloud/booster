package com.boostercloud.framework.core.services.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;

/**
 * The graphql-js {@code GraphQLFieldResolver<unknown, GraphQLResolverContext, args>}: a resolver called with
 * {@code (parent, args, context, info)}. It is a graphql-java {@link DataFetcher}, which takes the source, the
 * coerced arguments, the {@link GraphQLResolverContext} ({@link Common#resolverContext(DataFetchingEnvironment)}) and
 * the environment (the {@code info}) from the {@link DataFetchingEnvironment}.
 */
@FunctionalInterface
public interface GraphQLFieldResolver extends DataFetcher<Object> {

  /**
   * Resolves the field.
   *
   * @param parent the parent value (graphql-js {@code source}), or {@code null} for root fields
   * @param args the coerced arguments (raw values)
   * @param context the resolver context, or {@code null}
   * @param info the execution environment, or {@code null} when called directly
   * @return the value of the field (for subscription fields, the publisher of events)
   */
  Object resolve(Object parent, Map<String, Object> args, GraphQLResolverContext context, DataFetchingEnvironment info);

  @Override
  default Object get(DataFetchingEnvironment environment) {
    return resolve(environment.getSource(), environment.getArguments(), Common.resolverContext(environment),
        environment);
  }
}
