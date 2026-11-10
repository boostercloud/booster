package com.boostercloud.rocket.webhook.types;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The parts of the WHATWG URL parser that decide whether the authority of a special URL ({@code http:},
 * {@code https:}, {@code ws:}, {@code wss:}, {@code ftp:}) is valid: the authority state (user information before the
 * last {@code @}), the host and port states, and the host parser (IPv6 in brackets, percent-decoding, domain to ASCII,
 * forbidden domain code points, IPv4 with hexadecimal and octal parts). {@code new URL(...)} throws
 * {@code TypeError: Invalid URL} exactly when one of them fails.
 *
 * <p>Domain to ASCII (UTS 46) is exact for ASCII host names, the only ones HTTP clients send. Non-ASCII labels and
 * {@code xn--} labels are checked with RFC 3492 Punycode and {@link IDN} (IDNA 2003), an approximation: UTS 46's
 * joiner and bidi rules and its non-transitional mappings ({@code ß}, {@code ς}) are not applied.
 */
final class WhatwgHost {

  private static final int PUNYCODE_BASE = 36;
  private static final int PUNYCODE_TMIN = 1;
  private static final int PUNYCODE_TMAX = 26;
  private static final int PUNYCODE_SKEW = 38;
  private static final int PUNYCODE_DAMP = 700;
  private static final int PUNYCODE_INITIAL_BIAS = 72;
  private static final int PUNYCODE_INITIAL_N = 128;

  private WhatwgHost() {}

  /**
   * The authority state, host state and port state of a special URL.
   *
   * @param authority the text between the slashes after the scheme and the first {@code /}, {@code \}, {@code ?} or
   *     {@code #} (tabs and newlines already removed)
   * @return whether the URL parser accepts it
   */
  static boolean isValidSpecialAuthority(String authority) {
    int at = authority.lastIndexOf('@');
    String hostAndPort = at < 0 ? authority : authority.substring(at + 1);
    boolean insideBrackets = false;
    int colon = -1;
    for (int i = 0; i < hostAndPort.length(); i++) {
      char c = hostAndPort.charAt(i);
      if (c == ':' && !insideBrackets) {
        colon = i;
        break;
      }
      if (c == '[') {
        insideBrackets = true;
      } else if (c == ']') {
        insideBrackets = false;
      }
    }
    String host = colon < 0 ? hostAndPort : hostAndPort.substring(0, colon);
    if (host.isEmpty()) {
      return false;
    }
    if (colon >= 0 && !isValidPort(hostAndPort.substring(colon + 1))) {
      return false;
    }
    return isValidHost(host);
  }

  private static boolean isValidPort(String port) {
    for (int i = 0; i < port.length(); i++) {
      if (!isAsciiDigit(port.charAt(i))) {
        return false;
      }
    }
    return port.isEmpty() || new BigInteger(port).compareTo(BigInteger.valueOf(65535)) <= 0;
  }

  /**
   * The host parser for special URLs.
   *
   * @param input the host (not empty)
   * @return whether it parses
   */
  static boolean isValidHost(String input) {
    if (input.startsWith("[")) {
      return input.endsWith("]") && input.length() >= 2 && isValidIpv6(input.substring(1, input.length() - 1));
    }
    String domain = utf8DecodeWithoutBom(percentDecode(input));
    String asciiDomain = domainToAscii(domain);
    if (asciiDomain == null || asciiDomain.isEmpty()) {
      return false;
    }
    for (int i = 0; i < asciiDomain.length(); i++) {
      if (isForbiddenDomainCodePoint(asciiDomain.charAt(i))) {
        return false;
      }
    }
    return !endsInANumber(asciiDomain) || isValidIpv4(asciiDomain);
  }

  private static boolean isForbiddenDomainCodePoint(char c) {
    return c <= 0x1F || c == 0x7F || " #%/:<>?@[\\]^|".indexOf(c) >= 0;
  }

  private static byte[] percentDecode(String input) {
    byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
        output.write(Character.digit(bytes[i + 1], 16) * 16 + Character.digit(bytes[i + 2], 16));
        i += 2;
      } else {
        output.write(bytes[i]);
      }
    }
    return output.toByteArray();
  }

  private static String utf8DecodeWithoutBom(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    return text.startsWith("﻿") ? text.substring(1) : text;
  }

  /**
   * Domain to ASCII with {@code beStrict} false.
   *
   * @param domain the percent-decoded host
   * @return the ASCII domain, or {@code null} on failure
   */
  private static String domainToAscii(String domain) {
    if (isAscii(domain)) {
      String lower = domain.toLowerCase(Locale.ROOT);
      if (strictSplit(lower, '.').stream().noneMatch(label -> label.startsWith("xn--"))) {
        return lower;
      }
    }
    String dotted = domain.replace('。', '.').replace('．', '.').replace('｡', '.');
    List<String> labels = new ArrayList<>();
    for (String label : strictSplit(dotted, '.')) {
      String ascii = labelToAscii(label);
      if (ascii == null) {
        return null;
      }
      labels.add(ascii);
    }
    return String.join(".", labels);
  }

  private static String labelToAscii(String label) {
    if (isAscii(label)) {
      String lower = label.toLowerCase(Locale.ROOT);
      if (!lower.startsWith("xn--")) {
        return lower;
      }
      String decoded = punycodeDecode(lower.substring(4));
      // UTS 46: an xn-- label must decode to a non-empty label with non-ASCII code points, already in its mapped form
      if (decoded == null || decoded.isEmpty() || isAscii(decoded)
          || !Normalizer.isNormalized(decoded, Normalizer.Form.NFC)) {
        return null;
      }
      String reencoded = idnToAscii(decoded);
      return lower.equals(reencoded) ? lower : null;
    }
    String ascii = idnToAscii(label);
    return ascii == null ? null : ascii.toLowerCase(Locale.ROOT);
  }

  private static String idnToAscii(String label) {
    try {
      return IDN.toASCII(label, IDN.ALLOW_UNASSIGNED);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  /**
   * RFC 3492 decoding of the part after {@code xn--}.
   *
   * @param input the encoded label
   * @return the decoded label, or {@code null} when it is not valid Punycode
   */
  static String punycodeDecode(String input) {
    int delimiter = input.lastIndexOf('-');
    List<Integer> output = new ArrayList<>();
    for (int j = 0; j < Math.max(delimiter, 0); j++) {
      char c = input.charAt(j);
      if (c >= 0x80) {
        return null;
      }
      output.add((int) c);
    }
    long n = PUNYCODE_INITIAL_N;
    long i = 0;
    int bias = PUNYCODE_INITIAL_BIAS;
    int position = delimiter > 0 ? delimiter + 1 : 0;
    while (position < input.length()) {
      long oldi = i;
      long w = 1;
      for (int k = PUNYCODE_BASE; ; k += PUNYCODE_BASE) {
        if (position >= input.length()) {
          return null;
        }
        int digit = punycodeDigit(input.charAt(position++));
        if (digit < 0) {
          return null;
        }
        i += digit * w;
        if (i > Integer.MAX_VALUE) {
          return null;
        }
        int t = k <= bias ? PUNYCODE_TMIN : k >= bias + PUNYCODE_TMAX ? PUNYCODE_TMAX : k - bias;
        if (digit < t) {
          break;
        }
        w *= PUNYCODE_BASE - t;
        if (w > Integer.MAX_VALUE) {
          return null;
        }
      }
      int size = output.size() + 1;
      bias = adapt(i - oldi, size, oldi == 0);
      n += i / size;
      i %= size;
      if (n > Character.MAX_CODE_POINT || (n >= 0xD800 && n <= 0xDFFF)) {
        return null;
      }
      output.add((int) i, (int) n);
      i++;
    }
    StringBuilder decoded = new StringBuilder();
    output.forEach(decoded::appendCodePoint);
    return decoded.toString();
  }

  private static int adapt(long delta, int numPoints, boolean firstTime) {
    delta = firstTime ? delta / PUNYCODE_DAMP : delta / 2;
    delta += delta / numPoints;
    int k = 0;
    while (delta > ((PUNYCODE_BASE - PUNYCODE_TMIN) * PUNYCODE_TMAX) / 2) {
      delta /= PUNYCODE_BASE - PUNYCODE_TMIN;
      k += PUNYCODE_BASE;
    }
    return (int) (k + (PUNYCODE_BASE - PUNYCODE_TMIN + 1) * delta / (delta + PUNYCODE_SKEW));
  }

  private static int punycodeDigit(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0' + 26;
    }
    if (c >= 'a' && c <= 'z') {
      return c - 'a';
    }
    if (c >= 'A' && c <= 'Z') {
      return c - 'A';
    }
    return -1;
  }

  private static boolean endsInANumber(String input) {
    List<String> parts = strictSplit(input, '.');
    if (parts.getLast().isEmpty()) {
      if (parts.size() == 1) {
        return false;
      }
      parts.removeLast();
    }
    String last = parts.getLast();
    if (!last.isEmpty() && last.chars().allMatch(c -> isAsciiDigit((char) c))) {
      return true;
    }
    return ipv4Number(last) != null;
  }

  private static boolean isValidIpv4(String input) {
    List<String> parts = strictSplit(input, '.');
    if (parts.getLast().isEmpty() && parts.size() > 1) {
      parts.removeLast();
    }
    if (parts.size() > 4) {
      return false;
    }
    List<BigInteger> numbers = new ArrayList<>();
    for (String part : parts) {
      BigInteger number = ipv4Number(part);
      if (number == null) {
        return false;
      }
      numbers.add(number);
    }
    BigInteger limit = BigInteger.valueOf(255);
    for (int index = 0; index < numbers.size() - 1; index++) {
      if (numbers.get(index).compareTo(limit) > 0) {
        return false;
      }
    }
    return numbers.getLast().compareTo(BigInteger.valueOf(256).pow(5 - numbers.size())) < 0;
  }

  private static BigInteger ipv4Number(String input) {
    if (input.isEmpty()) {
      return null;
    }
    int radix = 10;
    String digits = input;
    if (digits.length() >= 2 && (digits.startsWith("0x") || digits.startsWith("0X"))) {
      digits = digits.substring(2);
      radix = 16;
    } else if (digits.length() >= 2 && digits.charAt(0) == '0') {
      digits = digits.substring(1);
      radix = 8;
    }
    if (digits.isEmpty()) {
      return BigInteger.ZERO;
    }
    for (int i = 0; i < digits.length(); i++) {
      if (Character.digit(digits.charAt(i), radix) < 0 || digits.charAt(i) >= 0x80) {
        return null;
      }
    }
    return new BigInteger(digits, radix);
  }

  /**
   * The IPv6 parser (without the brackets).
   *
   * @param input the address
   * @return whether it parses
   */
  static boolean isValidIpv6(String input) {
    int pieceIndex = 0;
    boolean compressed = false;
    int pointer = 0;
    int length = input.length();
    if (pointer < length && input.charAt(pointer) == ':') {
      if (pointer + 1 >= length || input.charAt(pointer + 1) != ':') {
        return false;
      }
      pointer += 2;
      pieceIndex++;
      compressed = true;
    }
    while (pointer < length) {
      if (pieceIndex == 8) {
        return false;
      }
      if (input.charAt(pointer) == ':') {
        if (compressed) {
          return false;
        }
        pointer++;
        pieceIndex++;
        compressed = true;
        continue;
      }
      int hexLength = 0;
      while (hexLength < 4 && pointer < length && isHex(input.charAt(pointer))) {
        pointer++;
        hexLength++;
      }
      if (pointer < length && input.charAt(pointer) == '.') {
        if (hexLength == 0) {
          return false;
        }
        pointer -= hexLength;
        if (pieceIndex > 6) {
          return false;
        }
        int numbersSeen = 0;
        while (pointer < length) {
          Integer ipv4Piece = null;
          if (numbersSeen > 0) {
            if (input.charAt(pointer) == '.' && numbersSeen < 4) {
              pointer++;
            } else {
              return false;
            }
          }
          if (pointer >= length || !isAsciiDigit(input.charAt(pointer))) {
            return false;
          }
          while (pointer < length && isAsciiDigit(input.charAt(pointer))) {
            int number = input.charAt(pointer) - '0';
            if (ipv4Piece == null) {
              ipv4Piece = number;
            } else if (ipv4Piece == 0) {
              return false;
            } else {
              ipv4Piece = ipv4Piece * 10 + number;
            }
            if (ipv4Piece > 255) {
              return false;
            }
            pointer++;
          }
          numbersSeen++;
          if (numbersSeen == 2 || numbersSeen == 4) {
            pieceIndex++;
          }
        }
        return numbersSeen == 4 && (compressed || pieceIndex == 8);
      } else if (pointer < length && input.charAt(pointer) == ':') {
        pointer++;
        if (pointer >= length) {
          return false;
        }
      } else if (pointer < length) {
        return false;
      }
      pieceIndex++;
    }
    return compressed || pieceIndex == 8;
  }

  private static List<String> strictSplit(String input, char delimiter) {
    List<String> parts = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= input.length(); i++) {
      if (i == input.length() || input.charAt(i) == delimiter) {
        parts.add(input.substring(start, i));
        start = i + 1;
      }
    }
    return parts;
  }

  private static boolean isAscii(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) >= 0x80) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHex(int c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }
}
