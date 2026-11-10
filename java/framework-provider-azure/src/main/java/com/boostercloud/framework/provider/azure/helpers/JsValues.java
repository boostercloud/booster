package com.boostercloud.framework.provider.azure.helpers;

import com.boostercloud.framework.types.BoosterJson;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JavaScript semantics the TypeScript adapters rely on, made explicit: truthiness, {@code parseInt(x, 10)},
 * {@code String(x)} and structural property access ({@code 'key' in object}, {@code object.key}) over raw maps and
 * records. Every place where the TypeScript code depends on one of these rules calls the helper of the same name, so
 * the rule is written once.
 */
public final class JsValues {

  private JsValues() {}

  /**
   * JavaScript truthiness: {@code null}, {@code false}, {@code 0}, {@code NaN} and {@code ""} are falsy, anything else
   * (including empty maps and lists) is truthy.
   *
   * @param value any value
   * @return whether {@code if (value)} would take the branch
   */
  public static boolean truthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof Number number) {
      double d = number.doubleValue();
      return d != 0 && !Double.isNaN(d);
    }
    if (value instanceof CharSequence text) {
      return !text.isEmpty();
    }
    return true;
  }

  /**
   * {@code parseInt(String(value), 10)}: parses the longest decimal-integer prefix after leading whitespace
   * ({@code "12abc"} is 12, {@code "abc"} is {@code NaN}).
   *
   * @param value any value
   * @return the parsed number, or {@link Double#NaN}
   */
  public static double parseInt(Object value) {
    if (value == null) {
      return Double.NaN;
    }
    String text = jsString(value).strip();
    int index = 0;
    boolean negative = false;
    if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
      negative = text.charAt(index) == '-';
      index++;
    }
    int start = index;
    while (index < text.length() && Character.isDigit(text.charAt(index)) && text.charAt(index) < 128) {
      index++;
    }
    if (index == start) {
      return Double.NaN;
    }
    double parsed = Double.parseDouble(text.substring(start, index));
    return negative ? -parsed : parsed;
  }

  /**
   * {@code Number.isFinite(x)}.
   *
   * @param value a number
   * @return whether it is finite
   */
  public static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /**
   * {@code String(value)} as JavaScript renders it: {@code null}/{@code undefined} as {@code "null"}, numbers like
   * {@code Number#toString}, lists joined with {@code ","}, other objects as {@code "[object Object]"}.
   *
   * @param value any value
   * @return its JavaScript string form
   */
  public static String jsString(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof CharSequence || value instanceof Boolean || value instanceof Enum<?>) {
      return value.toString();
    }
    if (value instanceof Double || value instanceof Float) {
      return BoosterJson.formatNumber(((Number) value).doubleValue());
    }
    if (value instanceof Number) {
      return value.toString();
    }
    if (value instanceof List<?> list) {
      return list.stream().map(item -> item == null ? "" : jsString(item)).collect(Collectors.joining(","));
    }
    if (value instanceof Map<?, ?> || value.getClass().isRecord()) {
      return "[object Object]";
    }
    return value.toString();
  }

  /**
   * {@code 'key' in holder} for raw maps (key present, even with a {@code null} value) and records (a component with
   * that name exists).
   *
   * @param holder a map, a record or anything else
   * @param key the property name
   * @return whether the property exists
   */
  public static boolean has(Object holder, String key) {
    if (holder instanceof Map<?, ?> map) {
      return map.containsKey(key);
    }
    if (holder != null && holder.getClass().isRecord()) {
      for (RecordComponent component : holder.getClass().getRecordComponents()) {
        if (component.getName().equals(key)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * {@code holder?.key} for raw maps and records.
   *
   * @param holder a map, a record, or anything else
   * @param key the property name
   * @return the value, or {@code null} when absent
   */
  public static Object get(Object holder, String key) {
    if (holder instanceof Map<?, ?> map) {
      return map.get(key);
    }
    if (holder != null && holder.getClass().isRecord()) {
      for (RecordComponent component : holder.getClass().getRecordComponents()) {
        if (component.getName().equals(key)) {
          try {
            component.getAccessor().setAccessible(true);
            return component.getAccessor().invoke(holder);
          } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
          }
        }
      }
    }
    return null;
  }
}
