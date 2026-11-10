package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.searcher.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates Booster filters ({@code FilterFor}), sort specifications ({@code SortFor}) and projections
 * ({@code ProjectionFor}) on raw JSON documents, the way the database queries of the real providers do.
 */
public final class InMemoryQueries {

  private InMemoryQueries() {}

  /**
   * @param document a raw document
   * @param filters a filter ({@code {property: {operator: value}}}, nested properties, {@code and}/{@code or}/
   *     {@code not}), or {@code null}
   * @return whether the document matches
   */
  public static boolean matches(Map<String, Object> document, Map<String, Object> filters) {
    if (filters == null) {
      return true;
    }
    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      if (!matchesEntry(document, entry.getKey(), entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesEntry(Map<String, Object> document, String key, Object condition) {
    return switch (key) {
      case "and" -> list(condition).stream().allMatch(filter -> matches(document, map(filter)));
      case "or" -> list(condition).stream().anyMatch(filter -> matches(document, map(filter)));
      case "not" -> !matches(document, map(condition));
      default -> matchesProperty(document == null ? null : document.get(key), condition);
    };
  }

  private static boolean matchesProperty(Object value, Object condition) {
    if (!(condition instanceof Map<?, ?> conditionMap)) {
      return jsonEquals(value, condition);
    }
    for (Map.Entry<?, ?> entry : conditionMap.entrySet()) {
      String key = String.valueOf(entry.getKey());
      boolean holds;
      if (Operation.isOperator(key)) {
        holds = applyOperator(Operation.fromKey(key), value, entry.getValue());
      } else if ("and".equals(key) || "or".equals(key) || "not".equals(key)) {
        holds = matchesEntry(value instanceof Map<?, ?> ? map(value) : null, key, entry.getValue());
      } else {
        holds = matchesProperty(value instanceof Map<?, ?> nested ? nested.get(key) : null, entry.getValue());
      }
      if (!holds) {
        return false;
      }
    }
    return true;
  }

  private static boolean applyOperator(Operation operation, Object value, Object operand) {
    Object rawOperand = BoosterJson.toRaw(operand);
    return switch (operation.key()) {
      case "eq" -> jsonEquals(value, rawOperand);
      case "ne" -> !jsonEquals(value, rawOperand);
      case "gt" -> value != null && compare(value, rawOperand) > 0;
      case "gte" -> value != null && compare(value, rawOperand) >= 0;
      case "lt" -> value != null && compare(value, rawOperand) < 0;
      case "lte" -> value != null && compare(value, rawOperand) <= 0;
      case "in" -> list(rawOperand).stream().anyMatch(candidate -> jsonEquals(value, candidate));
      case "beginsWith" -> value instanceof String text && text.startsWith(String.valueOf(rawOperand));
      case "contains" -> switch (value) {
        case String text -> text.contains(String.valueOf(rawOperand));
        case Collection<?> collection -> collection.stream().anyMatch(element -> jsonEquals(element, rawOperand));
        case null, default -> false;
      };
      case "includes" -> switch (value) {
        case String text -> text.contains(String.valueOf(rawOperand));
        case Collection<?> collection -> collection.stream().anyMatch(element -> includes(element, rawOperand));
        case null, default -> false;
      };
      case "regex" -> value instanceof String text && Pattern.compile(String.valueOf(rawOperand)).matcher(text).find();
      case "iRegex" -> value instanceof String text
          && Pattern.compile(String.valueOf(rawOperand), Pattern.CASE_INSENSITIVE).matcher(text).find();
      case "isDefined" -> Boolean.TRUE.equals(rawOperand) == (value != null);
      default -> throw new IllegalArgumentException("Unsupported operator " + operation.key());
    };
  }

  /** An element includes the operand: equal, or (for objects) containing every property of the operand. */
  private static boolean includes(Object element, Object operand) {
    if (operand instanceof Map<?, ?> partial && element instanceof Map<?, ?> object) {
      return partial.entrySet().stream().allMatch(entry -> jsonEquals(object.get(entry.getKey()), entry.getValue()));
    }
    return jsonEquals(element, operand);
  }

  /**
   * @param left a raw value
   * @param right another value
   * @return whether both have the same JSON form (numbers compared by value)
   */
  public static boolean jsonEquals(Object left, Object right) {
    Object rawLeft = BoosterJson.toRaw(left);
    Object rawRight = BoosterJson.toRaw(right);
    if (rawLeft instanceof Number a && rawRight instanceof Number b) {
      return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
    }
    return rawLeft == null ? rawRight == null : rawLeft.equals(rawRight);
  }

  private static int compare(Object left, Object right) {
    if (left instanceof Number a && right instanceof Number b) {
      return Double.compare(a.doubleValue(), b.doubleValue());
    }
    return String.valueOf(left).compareTo(String.valueOf(right));
  }

  /**
   * @param sortBy a sort specification ({@code {property: "ASC" | "DESC" | nested specification}}), or {@code null}
   * @return the comparator of raw documents (no order for an empty specification)
   */
  public static Comparator<Map<String, Object>> comparator(Map<String, Object> sortBy) {
    Comparator<Map<String, Object>> comparator = (a, b) -> 0;
    if (sortBy == null) {
      return comparator;
    }
    for (SortKey key : sortKeys(sortBy, List.of())) {
      Comparator<Map<String, Object>> byKey = (a, b) -> compareNullsFirst(read(a, key.path()), read(b, key.path()));
      comparator = comparator.thenComparing(key.descending() ? byKey.reversed() : byKey);
    }
    return comparator;
  }

  private record SortKey(List<String> path, boolean descending) {}

  private static List<SortKey> sortKeys(Map<String, Object> sortBy, List<String> prefix) {
    List<SortKey> keys = new ArrayList<>();
    sortBy.forEach((property, direction) -> {
      List<String> path = new ArrayList<>(prefix);
      path.add(property);
      if (direction instanceof Map<?, ?> nested) {
        keys.addAll(sortKeys(map(nested), path));
      } else {
        keys.add(new SortKey(path, "DESC".equalsIgnoreCase(String.valueOf(direction))));
      }
    });
    return keys;
  }

  private static Object read(Map<String, Object> document, List<String> path) {
    Object current = document;
    for (String segment : path) {
      if (!(current instanceof Map<?, ?> object)) {
        return null;
      }
      current = object.get(segment);
    }
    return current;
  }

  private static int compareNullsFirst(Object left, Object right) {
    if (left == null || right == null) {
      return left == null ? (right == null ? 0 : -1) : 1;
    }
    return compare(left, right);
  }

  /**
   * @param document a raw document
   * @param select the selected paths ({@code a.b}, {@code items[].sku}), or {@code null} for the whole document
   * @return a new document with the selected paths only
   */
  public static Map<String, Object> project(Map<String, Object> document, List<String> select) {
    if (select == null) {
      return document;
    }
    Map<String, Object> projected = new LinkedHashMap<>();
    for (String path : select) {
      copyPath(document, projected, List.of(path.split("\\.")));
    }
    return projected;
  }

  @SuppressWarnings("unchecked")
  private static void copyPath(Map<String, Object> source, Map<String, Object> target, List<String> segments) {
    if (source == null || segments.isEmpty()) {
      return;
    }
    String segment = segments.getFirst();
    boolean array = segment.endsWith("[]");
    String property = array ? segment.substring(0, segment.length() - 2) : segment;
    if (!source.containsKey(property)) {
      return;
    }
    Object value = source.get(property);
    List<String> rest = segments.subList(1, segments.size());
    if (rest.isEmpty()) {
      target.put(property, value);
    } else if (array && value instanceof List<?> elements) {
      List<Object> projectedElements = target.get(property) instanceof List<?> existing
          ? (List<Object>) existing
          : new ArrayList<>();
      for (int i = 0; i < elements.size(); i++) {
        if (projectedElements.size() <= i) {
          projectedElements.add(new LinkedHashMap<String, Object>());
        }
        if (elements.get(i) instanceof Map<?, ?> element && projectedElements.get(i) instanceof Map<?, ?> projected) {
          copyPath((Map<String, Object>) element, (Map<String, Object>) projected, rest);
        }
      }
      target.put(property, projectedElements);
    } else if (value instanceof Map<?, ?> nested) {
      Map<String, Object> projectedNested = target.get(property) instanceof Map<?, ?> existing
          ? (Map<String, Object>) existing
          : new LinkedHashMap<>();
      copyPath((Map<String, Object>) nested, projectedNested, rest);
      target.put(property, projectedNested);
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  static List<?> list(Object value) {
    return value instanceof Collection<?> collection ? new ArrayList<>(collection) : List.of();
  }
}
