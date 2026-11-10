package com.boostercloud.rocket.webhook.core.busboy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Port of {@code busboy@1.6.0/lib/utils.js}: {@code Content-Type} and {@code Content-Disposition} parsing, the
 * charset decoders and {@code basename}. Strings that hold header bytes are Latin-1 (one char per byte), as Busboy
 * reads them with {@code latin1Slice}.
 */
public final class BusboyUtils {

  /** RFC 7230 {@code tchar}. */
  static final boolean[] TOKEN = table("!#$%&'*+-.^_`|~", true);
  /** Quoted-string text: tab, visible ASCII except {@code "} and {@code \}, and 0x80-0xFF. */
  static final boolean[] QDTEXT = qdtext();
  /** RFC 5987 {@code mime-charsetc}. */
  static final boolean[] CHARSET = table("!#$%&+-^_`{}~", true);
  /** RFC 5987 {@code attr-char}. */
  static final boolean[] EXTENDED_VALUE = table("!#$&+-.^_`|~", true);

  private BusboyUtils() {}

  private static boolean[] table(String symbols, boolean alphanumeric) {
    boolean[] table = new boolean[256];
    if (alphanumeric) {
      for (char c = '0'; c <= '9'; c++) {
        table[c] = true;
      }
      for (char c = 'A'; c <= 'Z'; c++) {
        table[c] = true;
        table[Character.toLowerCase(c)] = true;
      }
    }
    for (char c : symbols.toCharArray()) {
      table[c] = true;
    }
    return table;
  }

  private static boolean[] qdtext() {
    boolean[] table = new boolean[256];
    table['\t'] = true;
    for (int c = 0x20; c <= 0x7E; c++) {
      table[c] = c != '"' && c != '\\';
    }
    for (int c = 0x80; c <= 0xFF; c++) {
      table[c] = true;
    }
    return table;
  }

  static boolean in(boolean[] table, int code) {
    return code >= 0 && code < 256 && table[code];
  }

  private static int hexValue(int code) {
    if (code >= '0' && code <= '9') {
      return code - '0';
    }
    if (code >= 'a' && code <= 'f') {
      return code - 'a' + 10;
    }
    if (code >= 'A' && code <= 'F') {
      return code - 'A' + 10;
    }
    return -1;
  }

  /**
   * A parsed {@code Content-Type}.
   *
   * @param type the lower-cased type
   * @param subtype the lower-cased subtype
   * @param params the parameters (lower-cased names, first occurrence wins)
   */
  public record ContentType(String type, String subtype, Map<String, String> params) {}

  /**
   * A parsed {@code Content-Disposition}.
   *
   * @param type the lower-cased disposition type
   * @param params the parameters (lower-cased names, first occurrence wins)
   */
  public record Disposition(String type, Map<String, String> params) {}

  /**
   * {@code parseContentType(str)}.
   *
   * @param str the header value
   * @return the content type, or {@code null} when malformed
   */
  public static ContentType parseContentType(String str) {
    if (str.isEmpty()) {
      return null;
    }
    Map<String, String> params = new LinkedHashMap<>();
    int i = 0;
    for (; i < str.length(); ++i) {
      int code = str.charAt(i);
      if (!in(TOKEN, code)) {
        if (code != '/' || i == 0) {
          return null;
        }
        break;
      }
    }
    if (i == str.length()) {
      return null;
    }
    String type = str.substring(0, i).toLowerCase(Locale.ROOT);
    int subtypeStart = ++i;
    for (; i < str.length(); ++i) {
      int code = str.charAt(i);
      if (!in(TOKEN, code)) {
        if (i == subtypeStart) {
          return null;
        }
        if (parseContentTypeParams(str, i, params) == null) {
          return null;
        }
        break;
      }
    }
    if (i == subtypeStart) {
      return null;
    }
    String subtype = str.substring(subtypeStart, i).toLowerCase(Locale.ROOT);
    return new ContentType(type, subtype, params);
  }

  private static Map<String, String> parseContentTypeParams(String str, int i, Map<String, String> params) {
    while (i < str.length()) {
      for (; i < str.length(); ++i) {
        int code = str.charAt(i);
        if (code != ' ' && code != '\t') {
          break;
        }
      }
      if (i == str.length()) {
        break;
      }
      if (str.charAt(i++) != ';') {
        return null;
      }
      for (; i < str.length(); ++i) {
        int code = str.charAt(i);
        if (code != ' ' && code != '\t') {
          break;
        }
      }
      if (i == str.length()) {
        return null;
      }
      int nameStart = i;
      for (; i < str.length(); ++i) {
        int code = str.charAt(i);
        if (!in(TOKEN, code)) {
          if (code != '=') {
            return null;
          }
          break;
        }
      }
      if (i == str.length()) {
        return null;
      }
      String name = str.substring(nameStart, i);
      ++i;
      if (i == str.length()) {
        return null;
      }
      StringBuilder value = new StringBuilder();
      if (str.charAt(i) == '"') {
        int valueStart = ++i;
        boolean escaping = false;
        for (; i < str.length(); ++i) {
          int code = str.charAt(i);
          if (code == '\\') {
            if (escaping) {
              valueStart = i;
              escaping = false;
            } else {
              value.append(str, valueStart, i);
              escaping = true;
            }
            continue;
          }
          if (code == '"') {
            if (escaping) {
              valueStart = i;
              escaping = false;
              continue;
            }
            value.append(str, valueStart, i);
            break;
          }
          if (escaping) {
            valueStart = i - 1;
            escaping = false;
          }
          if (!in(QDTEXT, code)) {
            return null;
          }
        }
        if (i == str.length()) {
          return null;
        }
        ++i;
      } else {
        int valueStart = i;
        for (; i < str.length(); ++i) {
          int code = str.charAt(i);
          if (!in(TOKEN, code)) {
            if (i == valueStart) {
              return null;
            }
            break;
          }
        }
        value.append(str, valueStart, i);
      }
      params.putIfAbsent(name.toLowerCase(Locale.ROOT), value.toString());
    }
    return params;
  }

  /**
   * {@code parseDisposition(str, defDecoder)}.
   *
   * @param str the header value
   * @param defDecoder the decoder of non-extended parameter values
   * @return the disposition, or {@code null} when malformed
   */
  public static Disposition parseDisposition(String str, Decoder defDecoder) {
    if (str.isEmpty()) {
      return null;
    }
    Map<String, String> params = new LinkedHashMap<>();
    int i = 0;
    for (; i < str.length(); ++i) {
      int code = str.charAt(i);
      if (!in(TOKEN, code)) {
        if (parseDispositionParams(str, i, params, defDecoder) == null) {
          return null;
        }
        break;
      }
    }
    String type = str.substring(0, i).toLowerCase(Locale.ROOT);
    return new Disposition(type, params);
  }

  private static Map<String, String> parseDispositionParams(
      String str, int i, Map<String, String> params, Decoder defDecoder) {
    while (i < str.length()) {
      for (; i < str.length(); ++i) {
        int code = str.charAt(i);
        if (code != ' ' && code != '\t') {
          break;
        }
      }
      if (i == str.length()) {
        break;
      }
      if (str.charAt(i++) != ';') {
        return null;
      }
      for (; i < str.length(); ++i) {
        int code = str.charAt(i);
        if (code != ' ' && code != '\t') {
          break;
        }
      }
      if (i == str.length()) {
        return null;
      }
      int nameStart = i;
      for (; i < str.length(); ++i) {
        int code = str.charAt(i);
        if (!in(TOKEN, code)) {
          if (code == '=') {
            break;
          }
          return null;
        }
      }
      if (i == str.length()) {
        return null;
      }
      String value;
      String name = str.substring(nameStart, i);
      if (name.charAt(name.length() - 1) == '*') {
        // Extended value
        int charsetStart = ++i;
        for (; i < str.length(); ++i) {
          int code = str.charAt(i);
          if (!in(CHARSET, code)) {
            if (code != '\'') {
              return null;
            }
            break;
          }
        }
        if (i == str.length()) {
          return null;
        }
        String charset = str.substring(charsetStart, i);
        ++i;
        for (; i < str.length(); ++i) {
          if (str.charAt(i) == '\'') {
            break;
          }
        }
        if (i == str.length()) {
          return null;
        }
        ++i;
        if (i == str.length()) {
          return null;
        }
        int valueStart = i;
        int encode = 0;
        StringBuilder text = new StringBuilder();
        for (; i < str.length(); ++i) {
          int code = str.charAt(i);
          if (!in(EXTENDED_VALUE, code)) {
            if (code == '%') {
              int hexUpper;
              int hexLower;
              if (i + 2 < str.length()
                  && (hexUpper = hexValue(str.charAt(i + 1))) != -1
                  && (hexLower = hexValue(str.charAt(i + 2))) != -1) {
                int byteVal = (hexUpper << 4) + hexLower;
                text.append(str, valueStart, i);
                text.append((char) byteVal);
                i += 2;
                valueStart = i + 1;
                if (byteVal >= 128) {
                  encode = 2;
                } else if (encode == 0) {
                  encode = 1;
                }
                continue;
              }
              return null;
            }
            break;
          }
        }
        text.append(str, valueStart, i);
        value = convertToUTF8(text.toString(), charset, encode);
        if (value == null) {
          return null;
        }
      } else {
        // Non-extended value
        ++i;
        if (i == str.length()) {
          return null;
        }
        StringBuilder text = new StringBuilder();
        if (str.charAt(i) == '"') {
          int valueStart = ++i;
          boolean escaping = false;
          for (; i < str.length(); ++i) {
            int code = str.charAt(i);
            if (code == '\\') {
              if (escaping) {
                valueStart = i;
                escaping = false;
              } else {
                text.append(str, valueStart, i);
                escaping = true;
              }
              continue;
            }
            if (code == '"') {
              if (escaping) {
                valueStart = i;
                escaping = false;
                continue;
              }
              text.append(str, valueStart, i);
              break;
            }
            if (escaping) {
              valueStart = i - 1;
              escaping = false;
            }
            if (!in(QDTEXT, code)) {
              return null;
            }
          }
          if (i == str.length()) {
            return null;
          }
          ++i;
        } else {
          int valueStart = i;
          for (; i < str.length(); ++i) {
            int code = str.charAt(i);
            if (!in(TOKEN, code)) {
              if (i == valueStart) {
                return null;
              }
              break;
            }
          }
          text.append(str, valueStart, i);
        }
        value = defDecoder.decode(text.toString(), 2);
        if (value == null) {
          return null;
        }
      }
      params.putIfAbsent(name.toLowerCase(Locale.ROOT), value);
    }
    return params;
  }

  /**
   * A Busboy decoder: turns bytes (or a Latin-1 string holding bytes) into text.
   */
  public interface Decoder {
    /**
     * @param latin1 the bytes as Latin-1 characters
     * @param hint {@code 0}/{@code 1}: the value had no byte above 0x7F (UTF-8 may return it as is); {@code 2}: it had
     * @return the text, or {@code null} when it cannot be decoded
     */
    String decode(String latin1, int hint);

    /**
     * @param bytes the bytes
     * @return the text, or {@code null} when it cannot be decoded
     */
    String decode(byte[] bytes);
  }

  /** {@code nullDecoder}: returns the Latin-1 string unchanged. */
  public static final Decoder NULL_DECODER = new Decoder() {
    @Override
    public String decode(String latin1, int hint) {
      return latin1;
    }

    @Override
    public String decode(byte[] bytes) {
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  };

  private static final Decoder UTF8 = new Decoder() {
    @Override
    public String decode(String latin1, int hint) {
      if (latin1.isEmpty()) {
        return "";
      }
      if (hint < 2) {
        return latin1;
      }
      return decode(latin1.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public String decode(byte[] bytes) {
      return bytes.length == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
  };

  private static final Decoder LATIN1 = new Decoder() {
    @Override
    public String decode(String latin1, int hint) {
      return latin1;
    }

    @Override
    public String decode(byte[] bytes) {
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  };

  private static final Decoder UTF16LE = new Decoder() {
    @Override
    public String decode(String latin1, int hint) {
      return latin1.isEmpty() ? "" : decode(latin1.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public String decode(byte[] bytes) {
      // Buffer#ucs2Slice ignores a trailing odd byte
      return new String(bytes, 0, bytes.length - (bytes.length % 2), StandardCharsets.UTF_16LE);
    }
  };

  private static final Decoder BASE64 = new Decoder() {
    @Override
    public String decode(String latin1, int hint) {
      return latin1.isEmpty() ? "" : decode(latin1.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public String decode(byte[] bytes) {
      return Base64.getEncoder().encodeToString(bytes);
    }
  };

  /** {@code decoders.other}: a {@code TextDecoder} for the label ({@code null} when the label is unknown). */
  private record OtherDecoder(String label) implements Decoder {
    @Override
    public String decode(String latin1, int hint) {
      return latin1.isEmpty() ? "" : decode(latin1.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public String decode(byte[] bytes) {
      if (bytes.length == 0) {
        return "";
      }
      Charset charset = textDecoderCharset(label);
      return charset == null ? null : new String(bytes, charset);
    }
  }

  private static Charset textDecoderCharset(String label) {
    String normalized = label.trim().toLowerCase(Locale.ROOT);
    // WHATWG: "utf-16" is UTF-16LE
    if (normalized.equals("utf-16") || normalized.equals("unicode")) {
      return StandardCharsets.UTF_16LE;
    }
    try {
      return Charset.forName(normalized);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * {@code getDecoder(charset)}.
   *
   * @param charset a charset label
   * @return its decoder
   */
  public static Decoder getDecoder(String charset) {
    return switch (charset) {
      case "utf-8", "utf8" -> UTF8;
      case "latin1", "ascii", "us-ascii", "iso-8859-1", "iso8859-1", "iso88591", "iso_8859-1", "windows-1252",
          "iso_8859-1:1987", "cp1252", "x-cp1252" -> LATIN1;
      case "utf16le", "utf-16le", "ucs2", "ucs-2" -> UTF16LE;
      case "base64" -> BASE64;
      default -> {
        String lower = charset.toLowerCase(Locale.ROOT);
        yield lower.equals(charset) ? new OtherDecoder(charset) : getDecoder(lower);
      }
    };
  }

  /**
   * {@code convertToUTF8(data, charset, hint)} for a Latin-1 string.
   *
   * @param latin1 the bytes as Latin-1 characters
   * @param charset the charset
   * @param hint the decoder hint
   * @return the text, or {@code null}
   */
  public static String convertToUTF8(String latin1, String charset, int hint) {
    return getDecoder(charset).decode(latin1, hint);
  }

  /**
   * {@code convertToUTF8(data, charset, 0)} for bytes.
   *
   * @param bytes the bytes
   * @param charset the charset
   * @return the text, or {@code null}
   */
  public static String convertToUTF8(byte[] bytes, String charset) {
    return getDecoder(charset).decode(bytes);
  }

  /**
   * {@code basename(path)}: the part after the last {@code /} or {@code \}; {@code .} and {@code ..} become empty.
   *
   * @param path a file name
   * @return its base name
   */
  public static String basename(String path) {
    if (path == null) {
      return "";
    }
    for (int i = path.length() - 1; i >= 0; --i) {
      char c = path.charAt(i);
      if (c == '/' || c == '\\') {
        String name = path.substring(i + 1);
        return name.equals("..") || name.equals(".") ? "" : name;
      }
    }
    return path.equals("..") || path.equals(".") ? "" : path;
  }
}
