package com.boostercloud.framework.provider.azure.infrastructure.functions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared helpers of the legacy (Functions v3, Node) definitions. */
final class LegacyFunctions {

  private LegacyFunctions() {}

  /**
   * {@code handler.split('.')[1]}: the export name of a handler identifier such as
   * {@code dist/index.boosterServeGraphQL}.
   *
   * @param handler the handler identifier
   * @return the export name
   */
  static String entryPoint(String handler) {
    String[] parts = handler.split("\\.", -1);
    return parts.length > 1 ? parts[1] : null;
  }

  /**
   * @param keysAndValues alternating keys and values
   * @return an ordered map
   */
  static Map<String, Object> ordered(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }
}
