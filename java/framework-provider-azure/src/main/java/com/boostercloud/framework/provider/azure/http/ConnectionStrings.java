package com.boostercloud.framework.provider.azure.http;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parser of Azure connection strings ({@code Key1=value1;Key2=value2;}). Keys are matched case-insensitively; a
 * value may contain {@code =} (base64 keys end with padding).
 */
public final class ConnectionStrings {

  private ConnectionStrings() {}

  /**
   * @param connectionString the connection string
   * @return the key/value pairs, keys lower-cased, in order
   */
  public static Map<String, String> parse(String connectionString) {
    Map<String, String> result = new LinkedHashMap<>();
    if (connectionString == null) {
      return result;
    }
    for (String part : connectionString.split(";")) {
      String trimmed = part.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      int separator = trimmed.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      result.put(trimmed.substring(0, separator).strip().toLowerCase(Locale.ROOT), trimmed.substring(separator + 1).strip());
    }
    return result;
  }
}
