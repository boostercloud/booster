package com.boostercloud.framework.types.concepts;

import com.boostercloud.metadata.PropertyMetadata;
import java.util.List;
import java.util.Objects;

/**
 * What {@code @ReadModel} registers in {@code config.readModels[className]}. Port of {@code ReadModelMetadata}.
 *
 * @param type the read model class
 * @param properties the read model properties (calculated fields carry their {@code dependencies})
 * @param authorizer the resolved authorizer (never {@code null})
 * @param before the before hooks, never {@code null}
 */
public record ReadModelMetadata(
    Class<?> type, List<PropertyMetadata> properties, ReadModelAuthorizer authorizer, List<ReadModelBeforeFunction> before) {

  /** Canonical constructor; copies the lists ({@code null} {@code before} becomes empty). */
  public ReadModelMetadata {
    Objects.requireNonNull(type, "type");
    properties = List.copyOf(properties);
    before = before == null ? List.of() : List.copyOf(before);
  }
}
