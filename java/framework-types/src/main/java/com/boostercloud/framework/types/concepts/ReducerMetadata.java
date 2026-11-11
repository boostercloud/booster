package com.boostercloud.framework.types.concepts;

/**
 * What {@code @Reduces} registers in {@code config.reducers[eventName]}. Port of {@code ReducerMetadata}.
 *
 * @param type the entity class owning the reducer
 * @param methodName the static reducer method name
 */
public record ReducerMetadata(Class<?> type, String methodName) {}
