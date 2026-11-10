package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CDK for Terraform's {@code deepMerge} and {@code addOverride} on raw values ({@code Map<String, Object>} /
 * {@code List<Object>}).
 *
 * <ul>
 *   <li>Objects in the source are merged key by key into the target; any other value (lists included) replaces the
 *       target value wholesale.
 *   <li>A source object merged into a target list indexes the list with its numeric keys, as JavaScript does when it
 *       treats an array as an object: {@code addOverride("url_path_map.0.path_rule", rules)} replaces the
 *       {@code path_rule} of the first {@code url_path_map}.
 *   <li>A merge that leaves an empty object deletes it.
 * </ul>
 */
public final class DeepMerge {

  private DeepMerge() {}

  /**
   * Merges every source into the target (mutating it).
   *
   * @param target the target object
   * @param sources the objects to merge, in order
   * @return the target
   */
  @SafeVarargs
  public static Map<String, Object> deepMerge(Map<String, Object> target, Map<String, Object>... sources) {
    for (Map<String, Object> source : sources) {
      mergeInto(target, source);
    }
    return target;
  }

  /**
   * {@code addOverride(path, value)}: writes the value into the overrides tree at a dot-separated path, creating (or
   * replacing non-object values by) intermediate objects.
   *
   * @param overrides the overrides tree
   * @param path a dot-separated path such as {@code url_path_map.0.path_rule}
   * @param value the value (a deep copy is stored)
   */
  public static void addOverride(Map<String, Object> overrides, String path, Object value) {
    String[] parts = path.split("\\.", -1);
    Map<String, Object> current = overrides;
    for (int i = 0; i < parts.length - 1; i++) {
      Object next = current.get(parts[i]);
      if (!(next instanceof Map<?, ?>)) {
        next = new LinkedHashMap<String, Object>();
        current.put(parts[i], next);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> nextMap = (Map<String, Object>) next;
      current = nextMap;
    }
    current.put(parts[parts.length - 1], deepCopy(value));
  }

  /**
   * @param value a raw value
   * @return a mutable deep copy (maps and lists copied, other values shared)
   */
  public static Object deepCopy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, item) -> copy.put(String.valueOf(key), deepCopy(item)));
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      for (Object item : list) {
        copy.add(deepCopy(item));
      }
      return copy;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static void mergeInto(Object target, Map<String, Object> source) {
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> sourceObject) {
        Object targetValue = get(target, key);
        if (!(targetValue instanceof Map<?, ?>) && !(targetValue instanceof List<?>)) {
          targetValue = new LinkedHashMap<String, Object>();
          if (!put(target, key, targetValue)) {
            continue;
          }
        }
        mergeInto(targetValue, (Map<String, Object>) sourceObject);
        if (targetValue instanceof Map<?, ?> merged && merged.isEmpty()) {
          remove(target, key);
        }
      } else {
        put(target, key, deepCopy(value));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Object get(Object container, String key) {
    if (container instanceof Map<?, ?> map) {
      return map.get(key);
    }
    List<Object> list = (List<Object>) container;
    int index = index(key);
    return index >= 0 && index < list.size() ? list.get(index) : null;
  }

  @SuppressWarnings("unchecked")
  private static boolean put(Object container, String key, Object value) {
    if (container instanceof Map<?, ?> map) {
      ((Map<String, Object>) map).put(key, value);
      return true;
    }
    List<Object> list = (List<Object>) container;
    int index = index(key);
    if (index < 0) {
      // JavaScript would set a named property on the array, which JSON.stringify drops
      return false;
    }
    while (list.size() <= index) {
      list.add(null);
    }
    list.set(index, value);
    return true;
  }

  @SuppressWarnings("unchecked")
  private static void remove(Object container, String key) {
    if (container instanceof Map<?, ?> map) {
      map.remove(key);
    } else {
      int index = index(key);
      List<Object> list = (List<Object>) container;
      if (index >= 0 && index < list.size()) {
        // `delete array[i]` leaves a hole that JSON.stringify writes as null
        list.set(index, null);
      }
    }
  }

  private static int index(String key) {
    if (key.isEmpty() || key.length() > 9) {
      return -1;
    }
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) < '0' || key.charAt(i) > '9') {
        return -1;
      }
    }
    if (key.length() > 1 && key.charAt(0) == '0') {
      return -1;
    }
    return Integer.parseInt(key);
  }
}
