package com.boostercloud.rocket.webhook.types;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Port of {@code helper.ts}: finds the webhook route a request is for.
 */
public final class Helper {

  private static final Pattern ROUTE_PREFIX = Pattern.compile("^(/api/|/)");
  private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*):");
  private static final Set<String> SPECIAL_SCHEMES = Set.of("http", "https", "ws", "wss", "ftp", "file");
  /** The message of the {@code TypeError} {@code new URL} throws. */
  static final String INVALID_URL = "Invalid URL";

  private Helper() {}

  /**
   * {@code getRouteFromRequest(request)}: the pathname of {@code new URL(req.url, 'http://' + req.headers.host + '/')}
   * without a leading {@code /api/} or, failing that, a leading {@code /}. Relative URLs (local: {@code /test?x=1})
   * and absolute ones (Azure: {@code https://app.azurewebsites.net/api/test?x=1}) both work; the query and fragment are
   * dropped, dot segments resolved and the path kept percent-encoded, as WHATWG URL parsing does.
   *
   * <p>Examples: {@code /test} → {@code test}, {@code /api/test} → {@code test}, {@code /clients/other} →
   * {@code clients/other}, {@code /test/} → {@code test/}.
   *
   * <p>As {@code new URL} does, the base must parse even when the URL is absolute: a {@code Host} header the URL parser
   * rejects (empty, with spaces, a port that is not a number below 65536, a broken IPv6 or IPv4 address...) fails, and
   * so does an absolute or scheme-relative URL with such an authority. A missing header gives the valid base
   * {@code http://undefined/}.
   *
   * @param request the rocket request
   * @return the route
   * @throws IllegalArgumentException {@code Cannot read properties of undefined (reading 'url')} when the request has
   *     no {@code req}; {@code Invalid URL} (TypeScript's {@code TypeError}) when the base or the URL does not parse
   */
  public static String getRouteFromRequest(WebhookRequest request) {
    WebhookHttpRequest req = request.req();
    if (req == null) {
      throw new IllegalArgumentException("Cannot read properties of undefined (reading 'url')");
    }
    String host = req.headers().get("host");
    if (!isValidBase(host == null ? "undefined" : host)) {
      throw new IllegalArgumentException(INVALID_URL);
    }
    String pathname = pathname(req.url());
    return ROUTE_PREFIX.matcher(pathname).replaceFirst("");
  }

  /**
   * Whether {@code 'http://' + host + '/'} parses.
   *
   * @param host the {@code Host} header text
   * @return whether the base URL is valid
   */
  static boolean isValidBase(String host) {
    return WhatwgHost.isValidSpecialAuthority(authority(stripSlashes(host.replaceAll("[\\t\\n\\r]", ""))));
  }

  private static String authority(String afterSlashes) {
    int end = 0;
    while (end < afterSlashes.length() && "/\\?#".indexOf(afterSlashes.charAt(end)) < 0) {
      end++;
    }
    return afterSlashes.substring(0, end);
  }

  /**
   * The {@code pathname} of a URL resolved against an {@code http:} base whose path is {@code /} (WHATWG URL rules for
   * the parts that decide the path).
   *
   * @param url an absolute or relative URL ({@code null} is TypeScript's {@code undefined}, i.e. {@code "undefined"})
   * @return the pathname, starting with {@code /} for special schemes
   * @throws IllegalArgumentException {@code Invalid URL} when the authority of an absolute or scheme-relative URL does
   *     not parse
   */
  static String pathname(String url) {
    String input = url == null ? "undefined" : url;
    input = trimC0AndSpace(input).replaceAll("[\\t\\n\\r]", "");
    var scheme = SCHEME.matcher(input);
    if (scheme.find()) {
      String name = scheme.group(1).toLowerCase(Locale.ROOT);
      String rest = input.substring(scheme.end());
      if (!SPECIAL_SCHEMES.contains(name)) {
        return opaqueOrHierarchicalPath(rest);
      }
      if (name.equals("http") && !startsWithTwoSlashes(rest)) {
        // Same scheme as the base without two slashes: a path relative to the base
        return relativePath(rest);
      }
      // Other special schemes take an authority after any number of slashes (file: hosts follow other rules)
      return pathAfterAuthority(stripSlashes(rest), !name.equals("file"));
    }
    if (startsWithTwoSlashes(input)) {
      return pathAfterAuthority(stripSlashes(input), true);
    }
    return relativePath(input);
  }

  private static String relativePath(String input) {
    if (input.isEmpty() || input.charAt(0) == '?' || input.charAt(0) == '#') {
      return "/";
    }
    if (input.charAt(0) == '/' || input.charAt(0) == '\\') {
      return specialPath(input.substring(1));
    }
    // A relative path: the base path is "/", so its directory is empty
    return specialPath(input);
  }

  private static String pathAfterAuthority(String afterSlashes, boolean validateAuthority) {
    String authority = authority(afterSlashes);
    if (validateAuthority && !WhatwgHost.isValidSpecialAuthority(authority)) {
      throw new IllegalArgumentException(INVALID_URL);
    }
    String rest = afterSlashes.substring(authority.length());
    if (rest.isEmpty() || rest.charAt(0) == '?' || rest.charAt(0) == '#') {
      return "/";
    }
    return specialPath(rest.substring(1));
  }

  private static String opaqueOrHierarchicalPath(String rest) {
    String withoutQuery = cutAtQueryOrFragment(rest);
    if (withoutQuery.startsWith("//")) {
      int slash = withoutQuery.indexOf('/', 2);
      return slash < 0 ? "" : withoutQuery.substring(slash);
    }
    return withoutQuery;
  }

  /**
   * WHATWG "path state" for special URLs over the text after the first slash: {@code /} and {@code \} separate
   * segments, {@code .}/{@code ..} (also percent-encoded) are resolved, other characters percent-encoded with the path
   * percent-encode set.
   */
  private static String specialPath(String text) {
    String path = cutAtQueryOrFragment(text);
    List<String> segments = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i <= path.length(); i++) {
      boolean end = i == path.length();
      char c = end ? 0 : path.charAt(i);
      if (end || c == '/' || c == '\\') {
        String segment = buffer.toString();
        if (isDoubleDot(segment)) {
          if (!segments.isEmpty()) {
            segments.removeLast();
          }
          if (end) {
            segments.add("");
          }
        } else if (isSingleDot(segment)) {
          if (end) {
            segments.add("");
          }
        } else {
          segments.add(segment);
        }
        buffer.setLength(0);
      } else if (Character.isHighSurrogate(c) && i + 1 < path.length() && Character.isLowSurrogate(path.charAt(i + 1))) {
        percentEncode(buffer, path.substring(i, i + 2));
        i++;
      } else if (Character.isSurrogate(c)) {
        percentEncode(buffer, "�");
      } else if (c < 0x20 || c > 0x7E || " \"#<>?`{}".indexOf(c) >= 0) {
        percentEncode(buffer, String.valueOf(c));
      } else {
        buffer.append(c);
      }
    }
    return "/" + String.join("/", segments);
  }

  private static String cutAtQueryOrFragment(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '?' || c == '#') {
        return text.substring(0, i);
      }
    }
    return text;
  }

  private static boolean isSingleDot(String segment) {
    return segment.equals(".") || segment.equalsIgnoreCase("%2e");
  }

  private static boolean isDoubleDot(String segment) {
    String lower = segment.toLowerCase(Locale.ROOT);
    return lower.equals("..") || lower.equals(".%2e") || lower.equals("%2e.") || lower.equals("%2e%2e");
  }

  private static void percentEncode(StringBuilder buffer, String text) {
    for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
      buffer.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xFF));
    }
  }

  private static boolean startsWithTwoSlashes(String input) {
    return input.length() >= 2 && isSlash(input.charAt(0)) && isSlash(input.charAt(1));
  }

  private static String stripSlashes(String input) {
    int start = 0;
    while (start < input.length() && isSlash(input.charAt(start))) {
      start++;
    }
    return input.substring(start);
  }

  private static boolean isSlash(char c) {
    return c == '/' || c == '\\';
  }

  private static String trimC0AndSpace(String input) {
    int start = 0;
    int end = input.length();
    while (start < end && input.charAt(start) <= 0x20) {
      start++;
    }
    while (end > start && input.charAt(end - 1) <= 0x20) {
      end--;
    }
    return input.substring(start, end);
  }
}
