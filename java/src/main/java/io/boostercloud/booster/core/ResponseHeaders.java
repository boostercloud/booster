package io.boostercloud.booster.core;
import java.util.*;
/** Shared response headers for fields/handlers in one HTTP response. Transport owns framing headers. */
public final class ResponseHeaders {
  private final Map<String,String> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  public synchronized void set(String name, String value) {
    if (name == null || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value == null || value.chars().anyMatch(character -> character < 32 && character != 9 || character == 127))
      throw new IllegalArgumentException("Invalid response header");
    values.put(name, value);
  }
  public synchronized Map<String,String> snapshot() { return Map.copyOf(values); }
}
