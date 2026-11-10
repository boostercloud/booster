package com.boostercloud.framework.provider.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builders of raw values (ordered maps and mutable lists, nulls allowed) for tests. */
public final class Raw {

  private Raw() {}

  /**
   * @param keysAndValues alternating keys and values
   * @return an ordered, mutable map
   */
  public static Map<String, Object> map(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("Odd number of arguments");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  /**
   * @param elements the elements
   * @return a mutable list
   */
  public static List<Object> list(Object... elements) {
    return new ArrayList<>(Arrays.asList(elements));
  }
}
