package com.boostercloud.framework.core.services.graphql;

/**
 * Port of {@code ResolverBuilder} ({@code services/graphql/common.ts}): builds the resolver of a field for a read
 * model, command or query class.
 */
@FunctionalInterface
public interface ResolverBuilder {
  /**
   * @param objectClass the read model, command or query class
   * @return the resolver (a graphql-java {@code DataFetcher}), or {@code null} for the default property resolver
   */
  GraphQLFieldResolver build(Class<?> objectClass);
}
