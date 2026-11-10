package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * A page of read models: {@code { items, count?, cursor? }}. Port of {@code ReadModelListResult}.
 *
 * <p>Provider searches always return this type; when the request was not paginated providers may leave
 * {@code count} and {@code cursor} unset and core returns only {@code items} (TypeScript returns a bare array).
 *
 * @param items the read models
 * @param count number of items, optional
 * @param cursor opaque provider cursor to pass back as {@code afterCursor}, optional
 * @param <T> the item type
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"items", "count", "cursor"})
public record ReadModelListResult<T>(List<T> items, Integer count, Map<String, String> cursor) {

  /** Canonical constructor; {@code null} items become empty. */
  public ReadModelListResult {
    items = items == null ? List.of() : List.copyOf(items);
  }

  /**
   * @param items the items
   * @param <T> the item type
   * @return a result without count and cursor
   */
  public static <T> ReadModelListResult<T> of(List<T> items) {
    return new ReadModelListResult<>(items, null, null);
  }
}
