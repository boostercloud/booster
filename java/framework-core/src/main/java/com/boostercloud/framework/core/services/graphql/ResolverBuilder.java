package com.boostercloud.framework.core.services.graphql;

import graphql.schema.DataFetcher;

/**
 * Port of {@code ResolverBuilder} ({@code services/graphql/common.ts}): builds the resolver of a field for a class.
 *
 * <p>Owned by stage <b>core-3</b> (spec 08 §2.3).
 */
@FunctionalInterface
public interface ResolverBuilder {
  /**
   * @param objectClass the read model, command or query class
   * @return the resolver
   */
  DataFetcher<Object> build(Class<?> objectClass);
}
