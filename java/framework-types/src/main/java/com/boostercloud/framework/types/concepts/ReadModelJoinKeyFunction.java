package com.boostercloud.framework.types.concepts;

import java.util.Map;

/**
 * Port of {@code ReadModelJoinKeyFunction}: given an entity, returns the read-model filter (same JSON shape as
 * {@code FilterFor}) selecting the read models to project, or {@code null} for none. Used with
 * {@code @Projects(entity = X.class, joinKeyFunction = MyJoinKey.class)}; core instantiates it with its no-argument
 * constructor.
 *
 * @param <TEntity> the entity type
 */
@FunctionalInterface
public interface ReadModelJoinKeyFunction<TEntity> {
  /**
   * @param entity the entity instance
   * @return the read model filter, or {@code null}
   */
  Map<String, Object> joinKey(TEntity entity);
}
