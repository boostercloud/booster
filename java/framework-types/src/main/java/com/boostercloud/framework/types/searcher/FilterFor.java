package com.boostercloud.framework.types.searcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The filter grammar of searches and subscriptions. TypeScript's {@code FilterFor<T>} is a compile-time type over a
 * plain JSON object; in Java a filter is that same JSON object as an ordered {@code Map<String, Object>}:
 *
 * <pre>
 * Filter     ::= { (property: Operation | "and": [Filter...] | "or": [Filter...] | "not": Filter | "isDefined": boolean)* }
 * Operation  ::= { (eq|ne|gt|gte|lt|lte|in|beginsWith|contains|regex|iRegex|includes|isDefined): value, ... }
 *              | Filter                       (nested object property)
 * </pre>
 *
 * The keys {@code and}, {@code or}, {@code not} and {@code isDefined} are reserved at every level. This class builds
 * such maps and classifies keys:
 *
 * <pre>{@code
 * Map<String, Object> filter = FilterFor.and(
 *     FilterFor.where("age", FilterFor.gt(30)),
 *     FilterFor.where("name", FilterFor.op(Operation.BEGINS_WITH, "Jo")));
 * }</pre>
 */
public final class FilterFor {

  /** {@code and}. */
  public static final String AND = "and";

  /** {@code or}. */
  public static final String OR = "or";

  /** {@code not}. */
  public static final String NOT = "not";

  /** {@code isDefined}. */
  public static final String IS_DEFINED = "isDefined";

  /** The keys reserved at every filter level. */
  public static final Set<String> RESERVED_KEYS = Set.of(AND, OR, NOT, IS_DEFINED);

  private FilterFor() {}

  /**
   * @param key a filter key
   * @return whether it is {@code and}, {@code or} or {@code not}
   */
  public static boolean isCombinator(String key) {
    return AND.equals(key) || OR.equals(key) || NOT.equals(key);
  }

  /**
   * @param property a property name (or a reserved key)
   * @param operation its operation (operator object, nested filter, or the list of a combinator)
   * @return {@code { property: operation }}
   */
  public static Map<String, Object> where(String property, Object operation) {
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(property, operation);
    return filter;
  }

  /**
   * Merges filters into one object (implicit AND); later keys replace earlier ones.
   *
   * @param filters filters
   * @return the merged filter
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Map<String, Object> all(Map<String, Object>... filters) {
    Map<String, Object> merged = new LinkedHashMap<>();
    Arrays.stream(filters).forEach(merged::putAll);
    return merged;
  }

  /**
   * @param filters sub-filters
   * @return {@code { and: [...] }}
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Map<String, Object> and(Map<String, Object>... filters) {
    return where(AND, List.of(filters));
  }

  /**
   * @param filters sub-filters
   * @return {@code { or: [...] }}
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static Map<String, Object> or(Map<String, Object>... filters) {
    return where(OR, List.of(filters));
  }

  /**
   * @param filter a sub-filter
   * @return {@code { not: filter }}
   */
  public static Map<String, Object> not(Map<String, Object> filter) {
    return where(NOT, filter);
  }

  /**
   * @param operation an operator
   * @param value its operand
   * @return {@code { operator: value }}
   */
  public static Map<String, Object> op(Operation operation, Object value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(operation.key(), value);
    return result;
  }

  /**
   * @param value the operand ({@code null} allowed)
   * @return {@code { eq: value }}
   */
  public static Map<String, Object> eq(Object value) {
    return op(Operation.EQ, value);
  }

  /**
   * @param value the operand ({@code null} allowed)
   * @return {@code { ne: value }}
   */
  public static Map<String, Object> ne(Object value) {
    return op(Operation.NE, value);
  }

  /**
   * @param value the operand
   * @return {@code { gt: value }}
   */
  public static Map<String, Object> gt(Object value) {
    return op(Operation.GT, value);
  }

  /**
   * @param value the operand
   * @return {@code { gte: value }}
   */
  public static Map<String, Object> gte(Object value) {
    return op(Operation.GTE, value);
  }

  /**
   * @param value the operand
   * @return {@code { lt: value }}
   */
  public static Map<String, Object> lt(Object value) {
    return op(Operation.LT, value);
  }

  /**
   * @param value the operand
   * @return {@code { lte: value }}
   */
  public static Map<String, Object> lte(Object value) {
    return op(Operation.LTE, value);
  }

  /**
   * @param values the candidates
   * @return {@code { in: [...] }}
   */
  public static Map<String, Object> in(Collection<?> values) {
    return op(Operation.IN, List.copyOf(values));
  }

  /**
   * @param value the operand
   * @return {@code { beginsWith: value }}
   */
  public static Map<String, Object> beginsWith(String value) {
    return op(Operation.BEGINS_WITH, value);
  }

  /**
   * @param value the operand
   * @return {@code { contains: value }}
   */
  public static Map<String, Object> contains(String value) {
    return op(Operation.CONTAINS, value);
  }

  /**
   * @param value the element (or a sub-object an element must match)
   * @return {@code { includes: value }}
   */
  public static Map<String, Object> includes(Object value) {
    return op(Operation.INCLUDES, value);
  }

  /**
   * @param defined whether the property must exist
   * @return {@code { isDefined: defined }}
   */
  public static Map<String, Object> isDefined(boolean defined) {
    return op(Operation.IS_DEFINED, defined);
  }

  /**
   * Tells an operator object ({@code {gt: 3}}) from a nested filter ({@code {street: {eq: "x"}}}): it is an operator
   * object when every key is an operator key.
   *
   * @param value the value of a property in a filter
   * @return whether it is an operator object
   */
  public static boolean isOperationObject(Object value) {
    if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
      return false;
    }
    return map.keySet().stream().allMatch(key -> key instanceof String text && Operation.isOperator(text));
  }
}
