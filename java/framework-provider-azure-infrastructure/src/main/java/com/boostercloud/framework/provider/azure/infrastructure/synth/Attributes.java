package com.boostercloud.framework.provider.azure.infrastructure.synth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds ordered attribute maps for the resource builders. */
public final class Attributes {

  private Attributes() {}

  /**
   * @param keysAndValues alternating keys (strings) and values; {@code null} values are skipped
   * @return an ordered, mutable map
   * @throws IllegalArgumentException when the arguments are not key/value pairs
   */
  public static Map<String, Object> of(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("Expected key/value pairs");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      Object value = keysAndValues[i + 1];
      if (value != null) {
        map.put((String) keysAndValues[i], value);
      }
    }
    return map;
  }
}
