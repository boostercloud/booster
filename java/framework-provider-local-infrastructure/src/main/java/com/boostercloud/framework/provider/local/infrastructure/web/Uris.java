package com.boostercloud.framework.provider.local.infrastructure.web;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The URL helpers of the Node stack: {@code decodeURIComponent}, the {@code encodeurl} and {@code escape-html}
 * packages, and the pathname/query split of {@code parseurl}.
 */
public final class Uris {

  private Uris() {}

  /**
   * JavaScript's {@code decodeURIComponent}: decodes {@code %XX} sequences as UTF-8 (a {@code +} stays a {@code +}).
   *
   * @param value the encoded text
   * @return the decoded text
   * @throws URIError {@code URI malformed} for a truncated escape or bytes that are not UTF-8
   */
  public static String decodeURIComponent(String value) {
    if (value.indexOf('%') < 0) {
      return value;
    }
    StringBuilder result = new StringBuilder(value.length());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    int i = 0;
    while (i < value.length()) {
      char c = value.charAt(i);
      if (c != '%') {
        result.append(c);
        i++;
        continue;
      }
      bytes.reset();
      while (i < value.length() && value.charAt(i) == '%') {
        if (i + 2 >= value.length()) {
          throw new URIError("URI malformed");
        }
        int high = Character.digit(value.charAt(i + 1), 16);
        int low = Character.digit(value.charAt(i + 2), 16);
        if (high < 0 || low < 0) {
          throw new URIError("URI malformed");
        }
        bytes.write((high << 4) | low);
        i += 3;
      }
      try {
        result.append(StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())));
      } catch (CharacterCodingException e) {
        throw new URIError("URI malformed");
      }
    }
    return result.toString();
  }

  /**
   * {@code decodeURIComponent} that returns the input unchanged instead of failing (the decoder of {@code qs}).
   *
   * @param value the encoded text
   * @return the decoded text, or {@code value} when it is malformed
   */
  public static String decodeLenient(String value) {
    try {
      return decodeURIComponent(value);
    } catch (URIError e) {
      return value;
    }
  }

  /**
   * The {@code encodeurl} package: percent-encodes every character that is not allowed in a URL, leaving valid
   * {@code %XX} escapes untouched.
   *
   * @param url the URL
   * @return the encoded URL
   */
  public static String encodeUrl(String url) {
    StringBuilder result = new StringBuilder(url.length());
    for (int i = 0; i < url.length(); i++) {
      char c = url.charAt(i);
      if (c == '%' && i + 2 < url.length() && isHex(url.charAt(i + 1)) && isHex(url.charAt(i + 2))) {
        result.append(c);
      } else if (c < 0x80 && isUrlCharacter(c)) {
        result.append(c);
      } else if (Character.isHighSurrogate(c) && i + 1 < url.length() && Character.isLowSurrogate(url.charAt(i + 1))) {
        appendEncoded(result, url.substring(i, i + 2));
        i++;
      } else if (Character.isSurrogate(c)) {
        appendEncoded(result, "�");
      } else {
        appendEncoded(result, String.valueOf(c));
      }
    }
    return result.toString();
  }

  private static boolean isHex(char c) {
    return Character.digit(c, 16) >= 0 && c < 0x80;
  }

  // encodeurl's ENCODE_CHARS_REGEXP keeps \x21 \x23-\x3B \x3D \x3F-\x5F \x61-\x7A \x7C \x7E
  private static boolean isUrlCharacter(char c) {
    return c == 0x21 || (c >= 0x23 && c <= 0x3B) || c == 0x3D || (c >= 0x3F && c <= 0x5F) || (c >= 0x61 && c <= 0x7A)
        || c == 0x7C || c == 0x7E;
  }

  private static void appendEncoded(StringBuilder result, String text) {
    for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
      result.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
          .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
    }
  }

  /**
   * The {@code escape-html} package.
   *
   * @param text any text
   * @return the text with {@code " & ' < >} escaped
   */
  public static String escapeHtml(String text) {
    StringBuilder result = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> result.append("&quot;");
        case '&' -> result.append("&amp;");
        case '\'' -> result.append("&#39;");
        case '<' -> result.append("&lt;");
        case '>' -> result.append("&gt;");
        default -> result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * @param url a path with an optional query string
   * @return the path without the query string ({@code parseurl(req).pathname})
   */
  public static String pathname(String url) {
    if (url == null) {
      return "/";
    }
    int query = url.indexOf('?');
    return query < 0 ? url : url.substring(0, query);
  }

  /**
   * @param url a path with an optional query string
   * @return the query string without {@code ?}, or {@code null} when there is none
   */
  public static String search(String url) {
    if (url == null) {
      return null;
    }
    int query = url.indexOf('?');
    return query < 0 ? null : url.substring(query + 1);
  }
}
