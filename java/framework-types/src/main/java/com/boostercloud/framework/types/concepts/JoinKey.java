package com.boostercloud.framework.types.concepts;

import java.util.Objects;

/**
 * The join key of a projection, port of the TypeScript union {@code keyof TEntity | ReadModelJoinKeyFunction}
 * (discriminated by {@code typeof joinKey === 'string'}).
 */
public sealed interface JoinKey permits JoinKey.EntityProperty, JoinKey.Query {

  /**
   * The name of an entity property whose value (or values, when it is a collection) are read model ids.
   *
   * @param propertyName the entity property name
   */
  record EntityProperty(String propertyName) implements JoinKey {
    /** Canonical constructor; rejects {@code null}. */
    public EntityProperty {
      Objects.requireNonNull(propertyName, "propertyName");
    }

    @Override
    public String toString() {
      return propertyName;
    }
  }

  /**
   * A function returning the filter of the read models to project. Two query join keys are equal when they hold
   * the same function class (TypeScript compares the function source text).
   *
   * @param function the join-key function
   */
  @SuppressWarnings("rawtypes")
  record Query(ReadModelJoinKeyFunction function) implements JoinKey {
    /** Canonical constructor; rejects {@code null}. */
    public Query {
      Objects.requireNonNull(function, "function");
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Query that && function.getClass().equals(that.function.getClass());
    }

    @Override
    public int hashCode() {
      return function.getClass().hashCode();
    }

    @Override
    public String toString() {
      return function.getClass().getName();
    }
  }

  /**
   * @param propertyName an entity property name
   * @return the join key
   */
  static JoinKey property(String propertyName) {
    return new EntityProperty(propertyName);
  }

  /**
   * @param function a join-key function
   * @return the join key
   */
  static JoinKey query(ReadModelJoinKeyFunction<?> function) {
    return new Query(function);
  }
}
