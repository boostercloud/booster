package com.boostercloud.framework.types.searcher;

import com.boostercloud.metadata.TypeGroup;
import java.util.EnumSet;
import java.util.Set;

/**
 * The operators of the filter grammar ({@code FilterFor}/{@code Operation}), with their JSON keys. Port of the
 * TypeScript operator interfaces ({@code BooleanOperators}, {@code ScalarOperators}, {@code StringOperators},
 * {@code ArrayOperators}, {@code IsDefinedOperator}); several operators in one operation object are AND-ed.
 */
public enum Operation {
  /** {@code eq}: equals (operand may be {@code null}). */
  EQ("eq"),
  /** {@code ne}: not equals (operand may be {@code null}). */
  NE("ne"),
  /** {@code gt}: greater than. */
  GT("gt"),
  /** {@code gte}: greater or equal. */
  GTE("gte"),
  /** {@code lt}: less than. */
  LT("lt"),
  /** {@code lte}: less or equal. */
  LTE("lte"),
  /** {@code in}: the value is one of a list. */
  IN("in"),
  /** {@code beginsWith}: string prefix. */
  BEGINS_WITH("beginsWith"),
  /** {@code contains}: substring. */
  CONTAINS("contains"),
  /** {@code regex}: regular expression match, case-sensitive. */
  REGEX("regex"),
  /** {@code iRegex}: regular expression match, case-insensitive. */
  I_REGEX("iRegex"),
  /** {@code includes}: the array contains the element (or an element matching the given object). */
  INCLUDES("includes"),
  /** {@code isDefined}: the property exists and is not {@code null} ({@code true}), or not ({@code false}). */
  IS_DEFINED("isDefined");

  private static final Set<Operation> BOOLEAN = EnumSet.of(EQ, NE, IS_DEFINED);
  private static final Set<Operation> SCALAR = EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IN, IS_DEFINED);
  private static final Set<Operation> STRING =
      EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IN, BEGINS_WITH, CONTAINS, REGEX, I_REGEX, IS_DEFINED);
  private static final Set<Operation> ARRAY = EnumSet.of(INCLUDES, IS_DEFINED);

  private final String key;

  Operation(String key) {
    this.key = key;
  }

  /**
   * @return the JSON key
   */
  public String key() {
    return key;
  }

  /**
   * @param key a JSON key
   * @return the operator, or {@code null} when the key is not an operator
   */
  public static Operation fromKey(String key) {
    for (Operation operation : values()) {
      if (operation.key.equals(key)) {
        return operation;
      }
    }
    return null;
  }

  /**
   * @param key a JSON key
   * @return whether it is an operator key
   */
  public static boolean isOperator(String key) {
    return fromKey(key) != null;
  }

  /**
   * The operators TypeScript allows for a property type ({@code Operation<T>}): arrays get {@code includes},
   * strings every scalar and string operator, numbers and dates the scalar ones, booleans {@code eq}/{@code ne}.
   *
   * @param typeGroup the property type group
   * @param isDate whether the property is a date
   * @return the allowed operators (empty for nested objects, which take a nested filter)
   */
  public static Set<Operation> allowedFor(TypeGroup typeGroup, boolean isDate) {
    if (isDate) {
      return SCALAR;
    }
    return switch (typeGroup) {
      case ARRAY, READONLY_ARRAY -> ARRAY;
      case STRING -> STRING;
      case NUMBER -> SCALAR;
      case BOOLEAN -> BOOLEAN;
      default -> Set.of();
    };
  }

  @Override
  public String toString() {
    return key;
  }
}
