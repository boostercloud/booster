package com.boostercloud.framework.types.searcher;

import java.util.List;

/**
 * The projection grammar ({@code ProjectionFor<T>}): a list of dotted property paths ({@code "id"},
 * {@code "address.city"}); a segment ending in {@code []} applies the rest of the path to every element of an array
 * ({@code "cartItems[].productId"}).
 */
public final class ProjectionFor {

  /** Suffix marking an array segment. */
  public static final String ARRAY_SUFFIX = "[]";

  private ProjectionFor() {}

  /**
   * @param paths projection paths
   * @return the projection
   */
  public static List<String> of(String... paths) {
    return List.of(paths);
  }

  /**
   * @param segment a path segment
   * @return whether it is an array segment ({@code name[]})
   */
  public static boolean isArraySegment(String segment) {
    return segment.endsWith(ARRAY_SUFFIX);
  }

  /**
   * @param segment a path segment
   * @return the property name without the array suffix
   */
  public static String propertyName(String segment) {
    return isArraySegment(segment) ? segment.substring(0, segment.length() - ARRAY_SUFFIX.length()) : segment;
  }
}
