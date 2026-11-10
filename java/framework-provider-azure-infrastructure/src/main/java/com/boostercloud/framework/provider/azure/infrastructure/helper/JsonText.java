package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.types.BoosterJson;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code JSON.stringify} for raw values ({@code Map}, {@code List}, strings, numbers, booleans, {@code null}), with the
 * exact text JavaScript produces: {@code JSON.stringify(value, null, 2)} writes {@code "key": value} with two-space
 * indentation, {@code {}} / {@code []} for empty containers, no trailing newline, and escapes only {@code "}, {@code \},
 * control characters and lone surrogates. Jackson's pretty printer ({@code "key" : value}) does not match it, and the
 * Azure infrastructure writes several files whose bytes are part of the contract ({@code host.json},
 * {@code function.json}, {@code cdk.tf.json}).
 *
 * <p>Other objects (records, beans) are first converted to raw values with {@link BoosterJson#toRaw(Object)}.
 */
public final class JsonText {

  private JsonText() {}

  /**
   * {@code JSON.stringify(value)}.
   *
   * @param value a raw value
   * @return the compact JSON text
   */
  public static String stringify(Object value) {
    StringBuilder out = new StringBuilder();
    write(out, value, 0, "", false);
    return out.toString();
  }

  /**
   * {@code JSON.stringify(value, null, indent)}.
   *
   * @param value a raw value
   * @param indent spaces per level ({@code 0} = compact)
   * @return the JSON text
   */
  public static String stringify(Object value, int indent) {
    StringBuilder out = new StringBuilder();
    write(out, value, indent, "", false);
    return out.toString();
  }

  /**
   * {@code JSON.stringify(value, null, indent)} with the keys of every object sorted (lexicographic order of the UTF-16
   * code units, JavaScript's default {@code sort}); list order is kept.
   *
   * @param value a raw value
   * @param indent spaces per level
   * @return the JSON text
   */
  public static String stringifySorted(Object value, int indent) {
    StringBuilder out = new StringBuilder();
    write(out, value, indent, "", true);
    return out.toString();
  }

  /**
   * A JSON string literal ({@code JSON.stringify(text)}).
   *
   * @param text the text
   * @return the quoted, escaped text
   */
  public static String quote(String text) {
    StringBuilder out = new StringBuilder(text.length() + 2);
    writeString(out, text);
    return out.toString();
  }

  private static void write(StringBuilder out, Object value, int indent, String currentIndent, boolean sortKeys) {
    Object raw = normalize(value);
    if (raw == null) {
      out.append("null");
    } else if (raw instanceof String text) {
      writeString(out, text);
    } else if (raw instanceof Boolean bool) {
      out.append(bool);
    } else if (raw instanceof Number number) {
      out.append(formatNumber(number));
    } else if (raw instanceof Map<?, ?> map) {
      writeObject(out, map, indent, currentIndent, sortKeys);
    } else if (raw instanceof List<?> list) {
      writeArray(out, list, indent, currentIndent, sortKeys);
    } else {
      writeString(out, raw.toString());
    }
  }

  private static Object normalize(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Optional<?> optional) {
      return normalize(optional.orElse(null));
    }
    if (value instanceof CharSequence sequence) {
      return sequence.toString();
    }
    if (value instanceof Character character) {
      return character.toString();
    }
    if (value instanceof Enum<?> constant) {
      return constant.toString();
    }
    if (value instanceof Boolean || value instanceof Number || value instanceof Map<?, ?> || value instanceof List<?>) {
      return value;
    }
    if (value instanceof Collection<?> collection) {
      return new ArrayList<>(collection);
    }
    if (value.getClass().isArray()) {
      List<Object> items = new ArrayList<>();
      for (int i = 0; i < Array.getLength(value); i++) {
        items.add(Array.get(value, i));
      }
      return items;
    }
    return BoosterJson.toRaw(value);
  }

  private static void writeObject(
      StringBuilder out, Map<?, ?> map, int indent, String currentIndent, boolean sortKeys) {
    Map<?, ?> entries = map;
    if (sortKeys) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      map.forEach((key, item) -> sorted.put(String.valueOf(key), item));
      entries = sorted;
    }
    if (entries.isEmpty()) {
      out.append("{}");
      return;
    }
    String innerIndent = currentIndent + " ".repeat(indent);
    out.append('{');
    boolean first = true;
    for (Map.Entry<?, ?> entry : entries.entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      if (indent > 0) {
        out.append('\n').append(innerIndent);
      }
      writeString(out, String.valueOf(entry.getKey()));
      out.append(indent > 0 ? ": " : ":");
      write(out, entry.getValue(), indent, innerIndent, sortKeys);
    }
    if (indent > 0) {
      out.append('\n').append(currentIndent);
    }
    out.append('}');
  }

  private static void writeArray(StringBuilder out, List<?> list, int indent, String currentIndent, boolean sortKeys) {
    if (list.isEmpty()) {
      out.append("[]");
      return;
    }
    String innerIndent = currentIndent + " ".repeat(indent);
    out.append('[');
    boolean first = true;
    for (Object item : list) {
      if (!first) {
        out.append(',');
      }
      first = false;
      if (indent > 0) {
        out.append('\n').append(innerIndent);
      }
      write(out, item, indent, innerIndent, sortKeys);
    }
    if (indent > 0) {
      out.append('\n').append(currentIndent);
    }
    out.append(']');
  }

  private static String formatNumber(Number number) {
    if (number instanceof Double || number instanceof Float) {
      double value = number.doubleValue();
      if (Double.isNaN(value) || Double.isInfinite(value)) {
        return "null";
      }
      return BoosterJson.formatNumber(value);
    }
    if (number instanceof BigDecimal decimal) {
      return decimal.toPlainString();
    }
    if (number instanceof BigInteger || number instanceof Long || number instanceof Integer
        || number instanceof Short || number instanceof Byte) {
      return number.toString();
    }
    return BoosterJson.formatNumber(number.doubleValue());
  }

  private static void writeString(StringBuilder out, String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            appendUnicodeEscape(out, c);
          } else if (Character.isHighSurrogate(c)) {
            if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
              out.append(c).append(text.charAt(i + 1));
              i++;
            } else {
              appendUnicodeEscape(out, c);
            }
          } else if (Character.isLowSurrogate(c)) {
            appendUnicodeEscape(out, c);
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  private static void appendUnicodeEscape(StringBuilder out, char c) {
    out.append("\\u");
    String hex = Integer.toHexString(c);
    out.append("0".repeat(4 - hex.length())).append(hex);
  }
}
