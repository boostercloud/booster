package com.boostercloud.framework.types.searcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The sort grammar ({@code SortFor<T>}): an ordered JSON object whose values are a direction ({@code "ASC"},
 * {@code "DESC"}, {@code "asc"}, {@code "desc"}) or a nested sort for nested properties. The key order is the sort
 * precedence, so always build sorts with insertion-ordered maps ({@link #of(Object...)} does).
 */
public final class SortFor {
  private SortFor() {}

  /**
   * @param propertiesAndDirections alternating property names and directions (or nested sort maps)
   * @return the ordered sort, e.g. {@code SortFor.of("name", "ASC", "age", "DESC")}
   */
  public static Map<String, Object> of(Object... propertiesAndDirections) {
    if (propertiesAndDirections.length % 2 != 0) {
      throw new IllegalArgumentException("SortFor.of needs property/direction pairs");
    }
    Map<String, Object> sort = new LinkedHashMap<>();
    for (int i = 0; i < propertiesAndDirections.length; i += 2) {
      Object direction = propertiesAndDirections[i + 1];
      sort.put(String.valueOf(propertiesAndDirections[i]),
          direction instanceof SortDirection value ? value.name() : direction);
    }
    return sort;
  }
}
