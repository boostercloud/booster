package com.boostercloud.framework.provider.azure.cosmos.rest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Orders {@code ORDER BY} values the way Cosmos DB does across types: undefined, then {@code null}, then booleans
 * ({@code false} before {@code true}), then numbers, then strings (ordinal), then arrays and objects (compared by their
 * JSON text, only to keep the order total). Used to merge the per-range results of cross-partition queries.
 */
final class CosmosValueComparator implements Comparator<Object> {

  /** Marker of an absent {@code item} (the document lacks the {@code ORDER BY} property). */
  static final Object UNDEFINED = new Object();

  static final CosmosValueComparator INSTANCE = new CosmosValueComparator();

  private CosmosValueComparator() {}

  @Override
  public int compare(Object left, Object right) {
    int byType = Integer.compare(rank(left), rank(right));
    if (byType != 0) {
      return byType;
    }
    if (left instanceof Boolean a && right instanceof Boolean b) {
      return Boolean.compare(a, b);
    }
    if (left instanceof Number a && right instanceof Number b) {
      return Double.compare(a.doubleValue(), b.doubleValue());
    }
    if (left instanceof String a && right instanceof String b) {
      return a.compareTo(b);
    }
    if (rank(left) >= 5) {
      return String.valueOf(left).compareTo(String.valueOf(right));
    }
    return 0;
  }

  private static int rank(Object value) {
    if (value == UNDEFINED) {
      return 0;
    }
    if (value == null) {
      return 1;
    }
    if (value instanceof Boolean) {
      return 2;
    }
    if (value instanceof Number) {
      return 3;
    }
    if (value instanceof String) {
      return 4;
    }
    if (value instanceof List<?>) {
      return 5;
    }
    if (value instanceof Map<?, ?>) {
      return 6;
    }
    return 7;
  }
}
