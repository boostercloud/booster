package com.boostercloud.framework.provider.local.infrastructure.web;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Content-type helpers with the rules of the Node packages Express uses ({@code content-type}, {@code type-is},
 * {@code mime} 1.x).
 */
public final class MimeTypes {

  /** {@code application/octet-stream}, the type of unknown extensions and binary bodies. */
  public static final String OCTET_STREAM = "application/octet-stream";

  private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
      Map.entry("html", "text/html"), Map.entry("htm", "text/html"), Map.entry("txt", "text/plain"),
      Map.entry("text", "text/plain"), Map.entry("css", "text/css"), Map.entry("csv", "text/csv"),
      Map.entry("md", "text/markdown"), Map.entry("markdown", "text/markdown"), Map.entry("xml", "application/xml"),
      Map.entry("js", "application/javascript"), Map.entry("mjs", "application/javascript"),
      Map.entry("json", "application/json"), Map.entry("map", "application/json"),
      Map.entry("form", "application/x-www-form-urlencoded"), Map.entry("urlencoded",
          "application/x-www-form-urlencoded"), Map.entry("bin", OCTET_STREAM), Map.entry("exe", OCTET_STREAM),
      Map.entry("pdf", "application/pdf"), Map.entry("zip", "application/zip"), Map.entry("gz", "application/gzip"),
      Map.entry("tar", "application/x-tar"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
      Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"), Map.entry("svg", "image/svg+xml"),
      Map.entry("webp", "image/webp"), Map.entry("ico", "image/x-icon"), Map.entry("bmp", "image/bmp"),
      Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("mp4", "video/mp4"),
      Map.entry("webm", "video/webm"), Map.entry("woff", "font/woff"), Map.entry("woff2", "font/woff2"),
      Map.entry("ttf", "font/ttf"), Map.entry("otf", "font/otf"), Map.entry("doc", "application/msword"),
      Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      Map.entry("xls", "application/vnd.ms-excel"),
      Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      Map.entry("ppt", "application/vnd.ms-powerpoint"),
      Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
      Map.entry("yaml", "text/yaml"), Map.entry("yml", "text/yaml"));

  private MimeTypes() {}

  /** A parsed content type: lower-cased {@code type/subtype} and its parameters (names lower-cased). */
  record ContentType(String mime, Map<String, String> parameters) {
    String format() {
      StringBuilder text = new StringBuilder(mime);
      parameters.forEach((name, value) -> text.append("; ").append(name).append('=').append(quoteIfNeeded(value)));
      return text.toString();
    }
  }

  /**
   * @param contentType a {@code Content-Type} value, or {@code null}
   * @return the lower-cased {@code type/subtype} without parameters, or {@code null}
   */
  public static String mimeOf(String contentType) {
    ContentType parsed = parse(contentType);
    return parsed == null ? null : parsed.mime();
  }

  /**
   * @param contentType a {@code Content-Type} value, or {@code null}
   * @return the lower-cased {@code charset} parameter, or {@code null}
   */
  public static String charsetOf(String contentType) {
    ContentType parsed = parse(contentType);
    String charset = parsed == null ? null : parsed.parameters().get("charset");
    return charset == null ? null : charset.toLowerCase(Locale.ROOT);
  }

  /**
   * {@code type-is}: whether a content type has the given mime type ({@code application/json} does not match
   * {@code application/vnd.api+json}).
   *
   * @param contentType the {@code Content-Type} header, or {@code null}
   * @param mime the expected mime type, possibly with a {@code *} subtype ({@code text/*})
   * @return whether it matches
   */
  public static boolean is(String contentType, String mime) {
    String actual = mimeOf(contentType);
    if (actual == null) {
      return false;
    }
    String expected = mime.toLowerCase(Locale.ROOT);
    if (expected.endsWith("/*")) {
      return actual.startsWith(expected.substring(0, expected.length() - 1));
    }
    if (expected.startsWith("*/")) {
      return actual.endsWith(expected.substring(1));
    }
    return actual.equals(expected);
  }

  /**
   * Express's {@code setCharset(type, charset)}: sets (or replaces) the {@code charset} parameter.
   *
   * @param contentType a content type
   * @param charset the charset
   * @return the content type with that charset
   */
  public static String setCharset(String contentType, String charset) {
    ContentType parsed = parse(contentType);
    if (parsed == null) {
      return contentType;
    }
    Map<String, String> parameters = new TreeMap<>(parsed.parameters());
    parameters.put("charset", charset);
    return new ContentType(parsed.mime(), parameters).format();
  }

  /**
   * Express's {@code res.set('Content-Type', value)} rule: when the value has no charset and {@code mime} knows a
   * default one (text types, JSON, JavaScript), {@code ; charset=utf-8} is appended.
   *
   * @param contentType the value
   * @return the value to send
   */
  public static String withDefaultCharset(String contentType) {
    if (contentType.toLowerCase(Locale.ROOT).contains("charset")) {
      return contentType;
    }
    String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/javascript")) {
      return contentType + "; charset=utf-8";
    }
    return contentType;
  }

  /**
   * Express's {@code res.type(type)}: a value containing {@code /} is used as is, anything else is looked up as an
   * extension ({@code html}, {@code .json}, {@code bin}).
   *
   * @param type a mime type or an extension
   * @return the mime type
   */
  public static String lookup(String type) {
    if (type.indexOf('/') >= 0) {
      return type;
    }
    String extension = type.startsWith(".") ? type.substring(1) : type;
    return BY_EXTENSION.getOrDefault(extension.toLowerCase(Locale.ROOT), OCTET_STREAM);
  }

  /**
   * The type {@code res.download} sends for a file: its extension's mime type, with {@code ; charset=UTF-8} for text
   * types (the {@code send} package).
   *
   * @param fileName a file name
   * @return the content type
   */
  public static String forFileName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    String mime = dot < 0 ? OCTET_STREAM : lookup(fileName.substring(dot + 1));
    if (mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/javascript")) {
      return mime + "; charset=UTF-8";
    }
    return mime;
  }

  static ContentType parse(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    String[] parts = contentType.split(";");
    String mime = parts[0].trim().toLowerCase(Locale.ROOT);
    if (!mime.matches("[!#$%&'*+.^_`|~0-9a-z-]+/[!#$%&'*+.^_`|~0-9a-z-]+")) {
      return null;
    }
    Map<String, String> parameters = new TreeMap<>();
    for (int i = 1; i < parts.length; i++) {
      String parameter = parts[i].trim();
      int equals = parameter.indexOf('=');
      if (equals <= 0) {
        continue;
      }
      String value = parameter.substring(equals + 1).trim();
      if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value = value.substring(1, value.length() - 1).replaceAll("\\\\(.)", "$1");
      }
      parameters.put(parameter.substring(0, equals).trim().toLowerCase(Locale.ROOT), value);
    }
    return new ContentType(mime, parameters);
  }

  private static String quoteIfNeeded(String value) {
    if (value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
      return value;
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
