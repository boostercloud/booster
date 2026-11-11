package com.boostercloud.framework.provider.azure.http;

import java.nio.charset.StandardCharsets;

/** JavaScript-compatible URI encoding. */
public final class UriEncoding {

  private static final String UNRESERVED_COMPONENT =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()";

  private UriEncoding() {}

  /**
   * JavaScript's {@code encodeURIComponent}: percent-encodes every UTF-8 byte except
   * {@code A-Z a-z 0-9 - _ . ! ~ * ' ( )}.
   *
   * @param value the text
   * @return the encoded text
   */
  public static String encodeURIComponent(String value) {
    StringBuilder out = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if (c < 128 && UNRESERVED_COMPONENT.indexOf(c) >= 0) {
        out.append(c);
      } else {
        out.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
            .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
      }
    }
    return out.toString();
  }
}
