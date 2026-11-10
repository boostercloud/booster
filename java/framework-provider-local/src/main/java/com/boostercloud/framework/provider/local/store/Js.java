package com.boostercloud.framework.provider.local.store;

import com.boostercloud.framework.types.BoosterJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * JavaScript semantics over raw values ({@code Map}, {@code List}, {@code String}, {@code Number}, {@code Boolean},
 * {@code null}, {@link Undefined}, {@link NedbDate}, {@link JsRegExp}). The NeDB port and the TypeScript-faithful
 * adapters use these helpers wherever the original code relies on JavaScript coercions: truthiness,
 * {@code parseInt}, {@code typeof}, property access, string conversion in template literals and
 * {@code JSON.stringify}.
 */
public final class Js {

  private Js() {}

  /**
   * {@code typeof value}.
   *
   * @param value a raw value
   * @return {@code "undefined"}, {@code "object"} (also for {@code null}, maps, lists, dates and regular expressions),
   *     {@code "boolean"}, {@code "number"}, {@code "string"} or {@code "function"}
   */
  public static String typeOf(Object value) {
    if (value == Undefined.INSTANCE) {
      return "undefined";
    }
    if (value instanceof Boolean) {
      return "boolean";
    }
    if (value instanceof Number) {
      return "number";
    }
    if (value instanceof String) {
      return "string";
    }
    if (value instanceof Predicate<?>) {
      return "function";
    }
    return "object";
  }

  /**
   * JavaScript truthiness: {@code undefined}, {@code null}, {@code false}, {@code 0}, {@code NaN} and {@code ""} are
   * falsy; everything else (including empty arrays and objects) is truthy.
   *
   * @param value a raw value
   * @return whether {@code if (value)} would take the branch
   */
  public static boolean truthy(Object value) {
    if (value == null || value == Undefined.INSTANCE) {
      return false;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      double d = n.doubleValue();
      return d != 0 && !Double.isNaN(d);
    }
    if (value instanceof String s) {
      return !s.isEmpty();
    }
    return true;
  }

  /**
   * {@code parseInt(String(value), 10)}: skips leading white space, accepts a sign and the longest run of decimal
   * digits, and returns {@code NaN} when there is none ({@code "5abc"} gives 5, {@code "abc"} gives {@code NaN}).
   *
   * @param value any value (converted with {@link #toJsString(Object)})
   * @return the parsed integer as a double, or {@code NaN}
   */
  public static double parseInt(Object value) {
    String text = toJsString(value);
    int i = 0;
    while (i < text.length() && isJsWhitespace(text.charAt(i))) {
      i++;
    }
    boolean negative = false;
    if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
      negative = text.charAt(i) == '-';
      i++;
    }
    int start = i;
    while (i < text.length() && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
      i++;
    }
    if (i == start) {
      return Double.NaN;
    }
    double result = new BigDecimal(text.substring(start, i)).doubleValue();
    return negative ? -result : result;
  }

  private static boolean isJsWhitespace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿';
  }

  /**
   * {@code Number(value)} (the {@code ToNumber} abstract operation).
   *
   * @param value a raw value
   * @return the number, possibly {@code NaN}
   */
  public static double toNumber(Object value) {
    if (value == null) {
      return 0;
    }
    if (value == Undefined.INSTANCE) {
      return Double.NaN;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    if (value instanceof Boolean b) {
      return b ? 1 : 0;
    }
    if (value instanceof NedbDate date) {
      return date.epochMillis();
    }
    if (value instanceof String s) {
      return stringToNumber(s);
    }
    if (value instanceof List<?>) {
      return stringToNumber(toJsString(value));
    }
    return Double.NaN;
  }

  private static double stringToNumber(String text) {
    String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      return 0;
    }
    switch (trimmed) {
      case "Infinity", "+Infinity" -> {
        return Double.POSITIVE_INFINITY;
      }
      case "-Infinity" -> {
        return Double.NEGATIVE_INFINITY;
      }
      default -> {
        // fall through to the numeric forms
      }
    }
    try {
      String lower = trimmed.toLowerCase();
      if (lower.startsWith("0x")) {
        return Long.parseLong(trimmed.substring(2), 16);
      }
      if (lower.startsWith("0o")) {
        return Long.parseLong(trimmed.substring(2), 8);
      }
      if (lower.startsWith("0b")) {
        return Long.parseLong(trimmed.substring(2), 2);
      }
      if (!trimmed.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
        return Double.NaN;
      }
      return Double.parseDouble(trimmed);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  /**
   * {@code String(value)}, as used by template literals ({@code `${value}`}).
   *
   * @param value a raw value (other objects use their {@code toString})
   * @return its JavaScript string form
   */
  public static String toJsString(Object value) {
    if (value == null) {
      return "null";
    }
    if (value == Undefined.INSTANCE) {
      return "undefined";
    }
    if (value instanceof String s) {
      return s;
    }
    if (value instanceof Number n) {
      return BoosterJson.formatNumber(n.doubleValue());
    }
    if (value instanceof Boolean b) {
      return b.toString();
    }
    if (value instanceof List<?> list) {
      StringJoiner joiner = new StringJoiner(",");
      for (Object element : list) {
        joiner.add(element == null || element == Undefined.INSTANCE ? "" : toJsString(element));
      }
      return joiner.toString();
    }
    if (value instanceof Map<?, ?>) {
      return "[object Object]";
    }
    return value.toString();
  }

  /**
   * {@code JSON.stringify(value)} for raw values; {@code undefined} gives the text {@code undefined} (what a template
   * literal prints for {@code JSON.stringify(undefined)}).
   *
   * @param value a raw value or any object {@code BoosterJson} can serialize
   * @return the JSON text
   */
  public static String stringify(Object value) {
    if (value == Undefined.INSTANCE) {
      return "undefined";
    }
    return Serialization.toJson(value);
  }

  /**
   * {@code obj[key]} on a raw value: map entries, array elements and {@code length}, string characters and
   * {@code length}; anything else is {@code undefined}.
   *
   * @param obj the object
   * @param key the property name
   * @return the property value, {@link Undefined#INSTANCE} when absent
   */
  public static Object get(Object obj, String key) {
    if (obj instanceof Map<?, ?> map) {
      if (map.containsKey(key)) {
        return map.get(key);
      }
      return Undefined.INSTANCE;
    }
    if (obj instanceof List<?> list) {
      if ("length".equals(key)) {
        return list.size();
      }
      int index = arrayIndex(key);
      return index >= 0 && index < list.size() ? list.get(index) : Undefined.INSTANCE;
    }
    if (obj instanceof String s) {
      if ("length".equals(key)) {
        return s.length();
      }
      int index = arrayIndex(key);
      return index >= 0 && index < s.length() ? String.valueOf(s.charAt(index)) : Undefined.INSTANCE;
    }
    return Undefined.INSTANCE;
  }

  /**
   * @param key a property name
   * @return the canonical array index it denotes ({@code "0"}, {@code "12"}), or {@code -1}
   */
  static int arrayIndex(String key) {
    if (key.isEmpty() || key.length() > 9 || (key.length() > 1 && key.charAt(0) == '0')) {
      return -1;
    }
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) < '0' || key.charAt(i) > '9') {
        return -1;
      }
    }
    return Integer.parseInt(key);
  }

  /**
   * {@code Object.keys(obj)}.
   *
   * @param obj a raw value
   * @return the own enumerable keys (map keys, array indices, string indices; none for other values)
   */
  public static List<String> keys(Object obj) {
    List<String> keys = new ArrayList<>();
    if (obj instanceof Map<?, ?> map) {
      for (Object key : map.keySet()) {
        keys.add(String.valueOf(key));
      }
    } else if (obj instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        keys.add(Integer.toString(i));
      }
    } else if (obj instanceof String s) {
      for (int i = 0; i < s.length(); i++) {
        keys.add(Integer.toString(i));
      }
    }
    return keys;
  }

  /**
   * {@code array.slice(start, end)} with JavaScript's argument normalisation ({@code NaN} is 0, negative values count
   * from the end, values past the end are clamped).
   *
   * @param list the array
   * @param start the start index
   * @param end the end index (exclusive)
   * @param <T> the element type
   * @return the slice (a new list)
   */
  public static <T> List<T> slice(List<T> list, double start, double end) {
    int length = list.size();
    int from = relativeIndex(start, length);
    int to = relativeIndex(end, length);
    return from >= to ? new ArrayList<>() : new ArrayList<>(list.subList(from, to));
  }

  private static int relativeIndex(double value, int length) {
    if (Double.isNaN(value)) {
      return 0;
    }
    double integer = value < 0 ? Math.ceil(value) : Math.floor(value);
    if (integer < 0) {
      return (int) Math.max(length + integer, 0);
    }
    return (int) Math.min(integer, length);
  }
}
