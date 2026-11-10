package com.boostercloud.framework.provider.local.infrastructure.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The query-string parser of Express ({@code qs} 6 with its defaults), used for {@code req.query} and for
 * {@code application/x-www-form-urlencoded} bodies ({@code extended: true}).
 *
 * <ul>
 *   <li>{@code a=1&b=2} gives {@code {a: "1", b: "2"}}; {@code a} alone gives {@code {a: ""}}; empty keys are
 *       skipped; {@code +} is a space; malformed escapes are kept verbatim.
 *   <li>repeated keys become arrays: {@code a=1&a=2} gives {@code {a: ["1", "2"]}}.
 *   <li>brackets nest, up to 5 levels: {@code a[b][c]=1} gives {@code {a: {b: {c: "1"}}}}; {@code a[]=1&a[]=2} and
 *       {@code a[0]=1&a[1]=2} give arrays (indexes above 20 give objects); deeper levels stay a literal key.
 *   <li>only the first 1000 parameters are read.
 * </ul>
 *
 * <p>Values are {@code String}s, {@code List<Object>}s and {@code Map<String, Object>}s (insertion ordered).
 */
public final class QueryStrings {

  /** {@code qs}' default {@code parameterLimit}. */
  public static final int PARAMETER_LIMIT = 1000;

  /** {@code qs}' default {@code depth}. */
  public static final int DEPTH = 5;

  /** {@code qs}' default {@code arrayLimit}. */
  public static final int ARRAY_LIMIT = 20;

  private static final Pattern BRACKETS = Pattern.compile("(\\[[^\\[\\]]*])");

  private QueryStrings() {}

  /**
   * @param text a query string without the leading {@code ?} (or {@code null})
   * @return the parsed object
   */
  public static Map<String, Object> parse(String text) {
    return parse(text, DEPTH, ARRAY_LIMIT, PARAMETER_LIMIT);
  }

  /**
   * {@code qs.parse} with explicit limits (body-parser's {@code urlencoded} uses depth 32 and an array limit of at
   * least 100).
   *
   * @param text the query string (or {@code null})
   * @param depth the maximum bracket nesting
   * @param arrayLimit the highest index that still builds an array
   * @param parameterLimit how many parameters are read
   * @return the parsed object
   */
  public static Map<String, Object> parse(String text, int depth, int arrayLimit, int parameterLimit) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (text == null || text.isEmpty()) {
      return result;
    }
    Map<String, Object> flat = new LinkedHashMap<>();
    String[] parts = text.split("&", -1);
    int count = Math.min(parts.length, parameterLimit);
    for (int i = 0; i < count; i++) {
      String part = parts[i];
      if (part.isEmpty()) {
        continue;
      }
      int bracketEquals = part.indexOf("]=");
      int position = bracketEquals == -1 ? part.indexOf('=') : bracketEquals + 1;
      String key;
      String value;
      if (position == -1) {
        key = decode(part);
        value = "";
      } else {
        key = decode(part.substring(0, position));
        value = decode(part.substring(position + 1));
      }
      if (flat.containsKey(key)) {
        flat.put(key, combine(flat.get(key), value));
      } else {
        flat.put(key, value);
      }
    }
    Object merged = result;
    for (Map.Entry<String, Object> entry : flat.entrySet()) {
      Object parsed = parseKeys(entry.getKey(), entry.getValue(), depth, arrayLimit);
      if (parsed != null) {
        merged = merge(merged, parsed);
      }
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> compacted = (Map<String, Object>) compact(merged);
    return compacted;
  }

  /**
   * body-parser's {@code parameterCount}: the number of {@code &}-separated parameters, or {@code -1} when there are
   * more than {@code limit}.
   *
   * @param body the urlencoded body
   * @param limit the parameter limit
   * @return the count or {@code -1}
   */
  public static int parameterCount(String body, int limit) {
    int count = 0;
    int index = 0;
    while ((index = body.indexOf('&', index)) != -1) {
      count++;
      index++;
      if (count == limit) {
        return -1;
      }
    }
    return count;
  }

  private static String decode(String text) {
    return Uris.decodeLenient(text.replace('+', ' '));
  }

  private static Object combine(Object existing, Object value) {
    List<Object> combined = new ArrayList<>();
    if (existing instanceof List<?> list) {
      combined.addAll(list);
    } else {
      combined.add(existing);
    }
    if (value instanceof List<?> list) {
      combined.addAll(list);
    } else {
      combined.add(value);
    }
    return combined;
  }

  /** A sparse array being built ({@code obj[index] = leaf}); compacted to a {@code List} at the end. */
  private static final class SparseArray extends TreeMap<Integer, Object> {
    private static final long serialVersionUID = 1L;

    void push(Object value) {
      put(isEmpty() ? 0 : lastKey() + 1, value);
    }
  }

  private static Object parseKeys(String givenKey, Object value, int maxDepth, int arrayLimit) {
    if (givenKey.isEmpty()) {
      return null;
    }
    Matcher first = BRACKETS.matcher(givenKey);
    boolean hasSegment = first.find();
    String parent = hasSegment ? givenKey.substring(0, first.start()) : givenKey;
    List<String> keys = new ArrayList<>();
    if (!parent.isEmpty()) {
      keys.add(parent);
    }
    Matcher child = BRACKETS.matcher(givenKey);
    int depth = 0;
    int remainderStart = -1;
    while (child.find()) {
      if (depth >= maxDepth) {
        remainderStart = child.start();
        break;
      }
      depth++;
      keys.add(child.group(1));
    }
    if (remainderStart >= 0) {
      keys.add("[" + givenKey.substring(remainderStart) + "]");
    }
    return parseObject(keys, value, arrayLimit);
  }

  private static Object parseObject(List<String> chain, Object value, int arrayLimit) {
    Object leaf = value;
    for (int i = chain.size() - 1; i >= 0; i--) {
      String root = chain.get(i);
      if (root.equals("[]")) {
        SparseArray array = new SparseArray();
        if (leaf instanceof List<?> list) {
          list.forEach(array::push);
        } else {
          array.push(leaf);
        }
        leaf = array;
        continue;
      }
      String cleanRoot = root.startsWith("[") && root.endsWith("]") ? root.substring(1, root.length() - 1) : root;
      Integer index = parseIndex(cleanRoot);
      if (index != null && !root.equals(cleanRoot) && index <= arrayLimit) {
        SparseArray array = new SparseArray();
        array.put(index, leaf);
        leaf = array;
      } else if (!cleanRoot.equals("__proto__")) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put(cleanRoot, leaf);
        leaf = object;
      } else {
        leaf = new LinkedHashMap<String, Object>();
      }
    }
    return leaf;
  }

  private static Integer parseIndex(String text) {
    if (text.isEmpty() || text.length() > 9) {
      return null;
    }
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isDigit(text.charAt(i)) || text.charAt(i) > '9') {
        return null;
      }
    }
    int value = Integer.parseInt(text);
    return String.valueOf(value).equals(text) ? value : null;
  }

  @SuppressWarnings("unchecked")
  private static Object merge(Object target, Object source) {
    if (source == null) {
      return target;
    }
    boolean sourceIsContainer = source instanceof Map<?, ?>;
    if (!sourceIsContainer) {
      if (target instanceof SparseArray array) {
        array.push(source);
        return array;
      }
      if (target instanceof Map<?, ?> map) {
        if (source instanceof String key) {
          ((Map<String, Object>) map).put(key, "true");
        }
        return map;
      }
      List<Object> combined = new ArrayList<>();
      combined.add(target);
      combined.add(source);
      return combined;
    }
    if (!(target instanceof Map<?, ?>)) {
      SparseArray array = new SparseArray();
      if (target instanceof List<?> list) {
        list.forEach(array::push);
      } else {
        array.push(target);
      }
      if (source instanceof SparseArray sourceArray) {
        sourceArray.values().forEach(array::push);
      } else {
        array.push(source);
      }
      return array;
    }
    if (target instanceof SparseArray targetArray && source instanceof SparseArray sourceArray) {
      for (Map.Entry<Integer, Object> entry : sourceArray.entrySet()) {
        Integer index = entry.getKey();
        if (targetArray.containsKey(index)) {
          Object existing = targetArray.get(index);
          if (existing instanceof Map<?, ?> && entry.getValue() instanceof Map<?, ?>) {
            targetArray.put(index, merge(existing, entry.getValue()));
          } else {
            targetArray.push(entry.getValue());
          }
        } else {
          targetArray.put(index, entry.getValue());
        }
      }
      return targetArray;
    }
    Map<String, Object> merged;
    if (target instanceof SparseArray targetArray) {
      merged = new LinkedHashMap<>();
      for (Map.Entry<Integer, Object> entry : targetArray.entrySet()) {
        merged.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    } else {
      merged = (Map<String, Object>) target;
    }
    Map<String, Object> sourceMap = new LinkedHashMap<>();
    if (source instanceof SparseArray sourceArray) {
      sourceArray.forEach((index, value) -> sourceMap.put(String.valueOf(index), value));
    } else {
      sourceMap.putAll((Map<String, Object>) source);
    }
    for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
      if (merged.containsKey(entry.getKey())) {
        merged.put(entry.getKey(), merge(merged.get(entry.getKey()), entry.getValue()));
      } else {
        merged.put(entry.getKey(), entry.getValue());
      }
    }
    return merged;
  }

  private static Object compact(Object value) {
    if (value instanceof SparseArray array) {
      List<Object> list = new ArrayList<>(array.size());
      for (Object element : array.values()) {
        list.add(compact(element));
      }
      return list;
    }
    if (value instanceof List<?> list) {
      List<Object> compacted = new ArrayList<>(list.size());
      for (Object element : list) {
        compacted.add(compact(element));
      }
      return compacted;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> compacted = new LinkedHashMap<>();
      map.forEach((key, element) -> compacted.put(String.valueOf(key), compact(element)));
      return compacted;
    }
    return value;
  }
}
