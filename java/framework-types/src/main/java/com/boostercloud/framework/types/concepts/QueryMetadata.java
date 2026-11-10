package com.boostercloud.framework.types.concepts;

import com.boostercloud.metadata.MethodMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.util.List;
import java.util.Objects;

/**
 * What {@code @Query} registers in {@code config.queryHandlers[className]}. Port of {@code QueryMetadata}.
 *
 * @param type the query class (with a static {@code handle(query, queryInfo)} method)
 * @param properties the query properties
 * @param methods the query methods
 * @param authorizer the resolved authorizer (never {@code null})
 * @param before the before hooks, never {@code null}
 */
public record QueryMetadata(
    Class<?> type,
    List<PropertyMetadata> properties,
    List<MethodMetadata> methods,
    QueryAuthorizer authorizer,
    List<QueryBeforeFunction> before) {

  /** Canonical constructor; copies the lists ({@code null} {@code before} becomes empty). */
  public QueryMetadata {
    Objects.requireNonNull(type, "type");
    properties = List.copyOf(properties);
    methods = List.copyOf(methods);
    before = before == null ? List.of() : List.copyOf(before);
  }
}
