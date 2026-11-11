package com.boostercloud.framework.provider.local.infrastructure.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Express 4 route paths ({@code path-to-regexp} 0.1): {@code :name} parameters (one segment, decoded),
 * {@code :name?} optional parameters, {@code :name(regex)} parameters with their own pattern, {@code *} wildcards
 * (any characters, captured as {@code "0"}, {@code "1"}...), and literal text. Matching is case-insensitive and
 * tolerates a trailing slash (Express's defaults); a mount path ({@code end = false}) matches whole leading segments.
 */
final class RoutePattern {

  /** A successful match. */
  record Match(String path, Map<String, String> params) {}

  private final String path;
  private final Pattern regex;
  private final List<String> keys;
  private final boolean fastSlash;

  private RoutePattern(String path, Pattern regex, List<String> keys, boolean fastSlash) {
    this.path = path;
    this.regex = regex;
    this.keys = keys;
    this.fastSlash = fastSlash;
  }

  String path() {
    return path;
  }

  static RoutePattern compile(String path, boolean end, boolean caseSensitive, boolean strict) {
    if (path.equals("/") && !end) {
      return new RoutePattern(path, null, List.of(), true);
    }
    List<String> keys = new ArrayList<>();
    StringBuilder pattern = new StringBuilder("^");
    int unnamed = 0;
    int i = 0;
    boolean lastWasLiteralSlash = false;
    while (i < path.length()) {
      char c = path.charAt(i);
      lastWasLiteralSlash = false;
      if (c == '\\' && i + 1 < path.length()) {
        pattern.append(Pattern.quote(String.valueOf(path.charAt(i + 1))));
        i += 2;
        continue;
      }
      boolean slashParam = c == '/' && i + 2 < path.length() && path.charAt(i + 1) == ':'
          && isWordChar(path.charAt(i + 2));
      if (slashParam || (c == ':' && i + 1 < path.length() && isWordChar(path.charAt(i + 1)))) {
        int nameStart = slashParam ? i + 2 : i + 1;
        int nameEnd = nameStart;
        while (nameEnd < path.length() && isWordChar(path.charAt(nameEnd))) {
          nameEnd++;
        }
        String name = path.substring(nameStart, nameEnd);
        String capture = "[^/]+?";
        int next = nameEnd;
        if (next < path.length() && path.charAt(next) == '(') {
          int close = closingParenthesis(path, next);
          if (close > next) {
            capture = path.substring(next + 1, close);
            next = close + 1;
          }
        }
        boolean optional = next < path.length() && path.charAt(next) == '?';
        if (optional) {
          next++;
        }
        keys.add(name);
        String group = "(" + capture + ")";
        if (slashParam) {
          pattern.append("(?:/").append(group).append(")");
        } else {
          pattern.append("(?:").append(group).append(")");
        }
        if (optional) {
          pattern.append('?');
        }
        i = next;
        continue;
      }
      if (c == '*') {
        keys.add(Integer.toString(unnamed++));
        pattern.append("(.*)");
      } else if (c == '/') {
        pattern.append('/');
        lastWasLiteralSlash = true;
      } else {
        pattern.append(Pattern.quote(String.valueOf(c)));
      }
      i++;
    }
    if (!strict) {
      pattern.append(lastWasLiteralSlash ? "?" : "/?");
    }
    pattern.append(end ? "$" : "(?=/|$)");
    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    return new RoutePattern(path, Pattern.compile(pattern.toString(), flags), List.copyOf(keys), false);
  }

  /**
   * @param pathname a raw (not decoded) request path
   * @return the match, or {@code null}
   * @throws URIError {@code Failed to decode param '<value>'} for a malformed parameter
   */
  Match match(String pathname) {
    if (fastSlash) {
      return new Match("", Map.of());
    }
    Matcher matcher = regex.matcher(pathname);
    if (!matcher.find()) {
      return null;
    }
    Map<String, String> params = new LinkedHashMap<>();
    for (int k = 0; k < keys.size() && k < matcher.groupCount(); k++) {
      String value = matcher.group(k + 1);
      if (value != null) {
        params.put(keys.get(k), decodeParam(value));
      }
    }
    return new Match(matcher.group(), Collections.unmodifiableMap(params));
  }

  private static String decodeParam(String value) {
    if (value.isEmpty()) {
      return value;
    }
    try {
      return Uris.decodeURIComponent(value);
    } catch (URIError e) {
      throw new URIError("Failed to decode param '" + value + "'");
    }
  }

  private static boolean isWordChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
  }

  private static int closingParenthesis(String text, int open) {
    int depth = 0;
    for (int i = open; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public String toString() {
    return path;
  }
}
