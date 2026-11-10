package com.boostercloud.framework.provider.local.store;

import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The NeDB line format: {@code model.serialize} / {@code model.deserialize} of {@code @seald-io/nedb}.
 *
 * <ul>
 *   <li>One document per line, written with {@code JSON.stringify} semantics (Booster's number formatting, absent
 *       values dropped from objects and written as {@code null} inside arrays).
 *   <li>Dates are written as {@code {"$$date": <epoch millis>}} and every object holding a {@code $$date} key is read
 *       back as a {@link NedbDate}.
 *   <li>The characters U+0085, U+2028 and U+2029 and UTF-16 surrogates are written as {@code \}{@code uXXXX} escapes:
 *       {@code JSON.stringify} leaves them raw, but NeDB's line reader treats the first three as line breaks (and would
 *       report the document as corrupt), and lone surrogates cannot be encoded in UTF-8. The escaped form is valid
 *       JSON that both NeDB and this store read back to the same string.
 *   <li>Reading uses strict JSON ({@code JSON.parse}): trailing characters after the value make the line corrupt.
 * </ul>
 */
public final class Serialization {

  private static final ObjectWriter WRITER = BoosterJson.mapper().writer().with(new NedbCharacterEscapes());
  private static final ObjectReader READER =
      BoosterJson.mapper().readerFor(Object.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private Serialization() {}

  /**
   * {@code model.serialize(document)}: the JSON line of a document (without the line terminator).
   *
   * @param document a raw document
   * @return its JSON text
   */
  public static String serialize(Object document) {
    return write(toTree(document, true));
  }

  /**
   * {@code JSON.stringify(value)}: dates as ISO strings, regular expressions as {@code {}}.
   *
   * @param value a raw value
   * @return its JSON text
   */
  public static String toJson(Object value) {
    return write(toTree(value, false));
  }

  private static String write(Object tree) {
    try {
      return WRITER.writeValueAsString(tree);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("Could not serialize value to JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * {@code model.deserialize(line)}: parses a JSON line and revives {@code $$date} objects.
   *
   * @param line the JSON text
   * @return the raw value
   * @throws IOException when the line is not valid JSON
   */
  public static Object deserialize(String line) throws IOException {
    return revive(READER.readValue(line));
  }

  private static Object revive(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> revived = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        revived.put(key, key.equals("$$date") ? toDate(entry.getValue()) : revive(entry.getValue()));
      }
      if (revived.containsKey("$$date")) {
        return revived.get("$$date");
      }
      return revived;
    }
    if (value instanceof List<?> list) {
      List<Object> revived = new ArrayList<>(list.size());
      for (Object element : list) {
        revived.add(revive(element));
      }
      return revived;
    }
    return value;
  }

  /** {@code new Date(value)}. */
  private static NedbDate toDate(Object value) {
    if (value instanceof Number number) {
      double millis = number.doubleValue();
      return new NedbDate(Double.isFinite(millis) ? (millis < 0 ? Math.ceil(millis) : Math.floor(millis)) : Double.NaN);
    }
    if (value == null) {
      return new NedbDate(0);
    }
    if (value instanceof Boolean b) {
      return new NedbDate(b ? 1 : 0);
    }
    if (value instanceof String s) {
      try {
        return NedbDate.of(com.boostercloud.framework.types.Dates.parse(s));
      } catch (RuntimeException e) {
        return new NedbDate(Double.NaN);
      }
    }
    return new NedbDate(Double.NaN);
  }

  /**
   * Converts a value into a plain JSON tree: special values replaced, absent map values dropped, absent array elements
   * written as {@code null}; other objects go through {@code BoosterJson.toRaw}.
   */
  static Object toTree(Object value, boolean nedbDates) {
    if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
      return value;
    }
    if (value == Undefined.INSTANCE) {
      return null;
    }
    if (value instanceof NedbDate date) {
      if (nedbDates) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("$$date", date.isValid() ? (Object) (long) date.epochMillis() : null);
        return wrapped;
      }
      return date.toIsoString();
    }
    if (value instanceof Instant instant) {
      return toTree(NedbDate.of(instant), nedbDates);
    }
    if (value instanceof Date date) {
      return toTree(NedbDate.of(date), nedbDates);
    }
    if (value instanceof JsRegExp || value instanceof Pattern) {
      return new LinkedHashMap<String, Object>();
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> tree = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getValue() != Undefined.INSTANCE) {
          tree.put(String.valueOf(entry.getKey()), toTree(entry.getValue(), nedbDates));
        }
      }
      return tree;
    }
    if (value instanceof Iterable<?> iterable) {
      List<Object> tree = new ArrayList<>();
      for (Object element : iterable) {
        tree.add(toTree(element, nedbDates));
      }
      return tree;
    }
    if (value instanceof Object[] array) {
      return toTree(List.of(array), nedbDates);
    }
    return toTree(BoosterJson.toRaw(value), nedbDates);
  }

  /** Escapes the characters NeDB's line reader would split on, and surrogates. */
  private static final class NedbCharacterEscapes extends CharacterEscapes {
    private static final long serialVersionUID = 1L;
    private final int[] asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();

    @Override
    public int[] getEscapeCodesForAscii() {
      return asciiEscapes;
    }

    @Override
    public SerializableString getEscapeSequence(int ch) {
      if (ch == 0x85 || ch == 0x2028 || ch == 0x2029 || (ch >= 0xD800 && ch <= 0xDFFF)) {
        return new SerializedString(String.format("\\u%04x", ch));
      }
      return null;
    }
  }
}
