package com.boostercloud.framework.types.searcher;

/**
 * Sort direction. TypeScript accepts {@code 'ASC' | 'DESC' | 'asc' | 'desc'}; {@link #parse(Object)} accepts all four.
 */
public enum SortDirection {
  /** Ascending. */
  ASC,
  /** Descending. */
  DESC;

  /**
   * @param value {@code ASC}, {@code DESC}, {@code asc}, {@code desc} or a {@link SortDirection}
   * @return the direction
   * @throws IllegalArgumentException for anything else
   */
  public static SortDirection parse(Object value) {
    if (value instanceof SortDirection direction) {
      return direction;
    }
    if ("ASC".equals(value) || "asc".equals(value)) {
      return ASC;
    }
    if ("DESC".equals(value) || "desc".equals(value)) {
      return DESC;
    }
    throw new IllegalArgumentException("Invalid sort direction '" + value + "'. Use ASC, DESC, asc or desc");
  }

  /**
   * @param value a sort value
   * @return whether it is one of the four accepted direction literals
   */
  public static boolean isDirection(Object value) {
    return "ASC".equals(value) || "asc".equals(value) || "DESC".equals(value) || "desc".equals(value)
        || value instanceof SortDirection;
  }
}
