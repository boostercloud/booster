package com.boostercloud.framework.types.concepts;

/**
 * One projection registered by {@code @Projects} in {@code config.projections[entityName]} (or
 * {@code config.unProjections}). Port of {@code ProjectionMetadata}.
 *
 * @param type the read model class owning the projection method
 * @param methodName the static projection method name
 * @param joinKey how entities are joined to read models
 */
public record ProjectionMetadata(Class<?> type, String methodName, JoinKey joinKey) {
  /**
   * @return {@code "<ReadModelClass>.<methodName>"}, the {@code projectionMethod} stored in {@code boosterMetadata}
   */
  public String projectionMethod() {
    return type.getSimpleName() + "." + methodName;
  }
}
