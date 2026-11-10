package com.boostercloud.framework.provider.local.infrastructure.web;

import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Express's body parsers ({@code body-parser} 1.20): {@link #json} ({@code express.json}), {@link #urlencoded}
 * ({@code express.urlencoded({ extended: true })}) and {@link #raw} ({@code express.raw}).
 *
 * <p>Every parser leaves requests alone when a previous parser already read the body, sets {@code req.body} to an
 * empty object when it is unset, ignores requests without a body ({@code transfer-encoding} or
 * {@code content-length}) or of another content type, inflates {@code gzip}/{@code deflate} bodies and fails with:
 * {@link PayloadTooLargeError} ({@code request entity too large}) above the limit, {@link UnsupportedMediaTypeError}
 * for an unsupported charset or content encoding, {@link BadRequestError} when the body cannot be read, and, for
 * JSON, {@link SyntaxError} for malformed JSON or a top-level value that is not an object or array ({@code strict}).
 */
public final class BodyParsers {

  /** body-parser's default limit, {@code 100kb}. */
  public static final long DEFAULT_LIMIT = 100 * 1024;

  private static final ObjectReader JSON_READER = BoosterJson.mapper().readerFor(Object.class)
      .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final Pattern BYTES = Pattern.compile("^((-|\\+)?(\\d+(?:\\.\\d+)?)) *(b|kb|mb|gb|tb|pb)?$",
      Pattern.CASE_INSENSITIVE);
  private static final int MAX_CONTEXT_CHARACTERS = 10;

  private BodyParsers() {}

  /** body-parser's {@code verify(req, res, buf)} option: inspects the raw bytes of a body before it is parsed. */
  @FunctionalInterface
  public interface Verifier {
    /**
     * @param request the request
     * @param response the response
     * @param body the raw (inflated) body bytes
     */
    void verify(HttpRequest request, HttpResponse response, byte[] body);
  }

  /**
   * {@code bytes.parse}: {@code "6mb"} is 6291456, {@code "100kb"} 102400, a bare number is bytes.
   *
   * @param text the size
   * @return the number of bytes
   */
  public static long parseBytes(String text) {
    Matcher matcher = BYTES.matcher(text.trim());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid size: " + text);
    }
    double value = Double.parseDouble(matcher.group(1));
    String unit = matcher.group(4) == null ? "b" : matcher.group(4).toLowerCase(Locale.ROOT);
    long multiplier = switch (unit) {
      case "kb" -> 1L << 10;
      case "mb" -> 1L << 20;
      case "gb" -> 1L << 30;
      case "tb" -> 1L << 40;
      case "pb" -> 1L << 50;
      default -> 1L;
    };
    return (long) Math.floor(value * multiplier);
  }

  /**
   * {@code express.json()} with the default limit and no verifier.
   *
   * @return the parser
   */
  public static RequestHandler json() {
    return json(DEFAULT_LIMIT, null);
  }

  /**
   * {@code express.json({ limit, verify })}: parses {@code application/json} bodies (any {@code utf-*} charset,
   * {@code utf-8} by default) into raw JSON values; an empty body gives {@code {}}.
   *
   * @param limit the maximum body size in bytes
   * @param verify called with the raw bytes before parsing, or {@code null}
   * @return the parser
   */
  public static RequestHandler json(long limit, Verifier verify) {
    return (request, response, next) -> {
      if (!prepare(request, "application/json")) {
        next.next();
        return;
      }
      String charset = charsetOrDefault(request);
      if (!charset.startsWith("utf-")) {
        next.next(unsupportedCharset(charset));
        return;
      }
      Object body;
      try {
        byte[] bytes = read(request, limit);
        if (verify != null) {
          verify.verify(request, response, bytes);
        }
        body = parseJson(decode(bytes, charset));
      } catch (RuntimeException e) {
        next.next(e);
        return;
      }
      request.body(body);
      next.next();
    };
  }

  /**
   * {@code express.urlencoded({ extended: true })} with the default limit.
   *
   * @return the parser
   */
  public static RequestHandler urlencoded() {
    return urlencoded(DEFAULT_LIMIT);
  }

  /**
   * {@code express.urlencoded({ extended: true, limit })}: parses {@code application/x-www-form-urlencoded} bodies
   * ({@code utf-8} only) with {@link QueryStrings} (depth 32, up to 1000 parameters).
   *
   * @param limit the maximum body size in bytes
   * @return the parser
   */
  public static RequestHandler urlencoded(long limit) {
    return (request, response, next) -> {
      if (!prepare(request, "application/x-www-form-urlencoded")) {
        next.next();
        return;
      }
      String charset = charsetOrDefault(request);
      if (!charset.equals("utf-8")) {
        next.next(unsupportedCharset(charset));
        return;
      }
      Object body;
      try {
        String text = new String(read(request, limit), StandardCharsets.UTF_8);
        int count = QueryStrings.parameterCount(text, QueryStrings.PARAMETER_LIMIT);
        if (count == -1) {
          throw new PayloadTooLargeError("too many parameters", "parameters.too.many");
        }
        body = text.isEmpty() ? new LinkedHashMap<String, Object>()
            : QueryStrings.parse(text, 32, Math.max(100, count + 1), QueryStrings.PARAMETER_LIMIT);
      } catch (RuntimeException e) {
        next.next(e);
        return;
      }
      request.body(body);
      next.next();
    };
  }

  /**
   * {@code express.raw()}: {@code application/octet-stream} bodies up to 100 kB as {@code byte[]}.
   *
   * @return the parser
   */
  public static RequestHandler raw() {
    return raw(DEFAULT_LIMIT, MimeTypes.OCTET_STREAM);
  }

  /**
   * {@code express.raw({ limit, type })}.
   *
   * @param limit the maximum body size in bytes
   * @param type the mime type to read ({@code *}{@code /*} for any)
   * @return the parser
   */
  public static RequestHandler raw(long limit, String type) {
    return (request, response, next) -> {
      if (!prepare(request, type)) {
        next.next();
        return;
      }
      try {
        request.body(read(request, limit));
      } catch (RuntimeException e) {
        next.next(e);
        return;
      }
      next.next();
    };
  }

  private static boolean prepare(HttpRequest request, String type) {
    if (request.isBodyRead()) {
      return false;
    }
    if (request.body() == null) {
      request.body(new LinkedHashMap<String, Object>());
    }
    if (!request.hasBody()) {
      return false;
    }
    return type.equals("*/*") || MimeTypes.is(request.contentType(), type);
  }

  private static String charsetOrDefault(HttpRequest request) {
    String charset = MimeTypes.charsetOf(request.contentType());
    return charset == null ? "utf-8" : charset;
  }

  private static UnsupportedMediaTypeError unsupportedCharset(String charset) {
    return new UnsupportedMediaTypeError("unsupported charset \"" + charset.toUpperCase(Locale.ROOT) + "\"",
        "charset.unsupported");
  }

  /**
   * Reads (and inflates) the whole body, enforcing the limit; marks the body as read.
   *
   * @param request the request
   * @param limit the limit in bytes
   * @return the bytes
   */
  static byte[] read(HttpRequest request, long limit) {
    String encoding = request.header("content-encoding");
    encoding = encoding == null ? "identity" : encoding.trim().toLowerCase(Locale.ROOT);
    long declared = contentLength(request);
    InputStream stream = request.rawStream();
    request.markBodyRead();
    if (encoding.equals("identity") && declared > limit) {
      throw new PayloadTooLargeError("request entity too large", "entity.too.large");
    }
    try {
      InputStream decoded = switch (encoding) {
        case "deflate" -> new InflaterInputStream(stream);
        case "gzip" -> new GZIPInputStream(stream);
        case "identity" -> stream;
        default -> throw new UnsupportedMediaTypeError("unsupported content encoding \"" + encoding + "\"",
            "encoding.unsupported");
      };
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = decoded.read(buffer)) != -1) {
        total += read;
        if (total > limit) {
          throw new PayloadTooLargeError("request entity too large", "entity.too.large");
        }
        bytes.write(buffer, 0, read);
      }
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new BadRequestError(e.getMessage() == null ? "request aborted" : e.getMessage(), "request.aborted", e);
    }
  }

  private static long contentLength(HttpRequest request) {
    String value = request.header("content-length");
    if (value == null) {
      return -1;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String decode(byte[] bytes, String charset) {
    Charset decoder;
    try {
      decoder = Charset.forName(charset);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      throw unsupportedCharset(charset);
    }
    String text = new String(bytes, decoder);
    return text.startsWith("﻿") ? text.substring(1) : text;
  }

  /**
   * body-parser's strict JSON parse: {@code ""} gives {@code {}}; the first non-blank character must be {@code {} or
   * {@code [}; otherwise, or when the text is not valid JSON, a {@link SyntaxError} with a V8-style message
   * ({@code Unexpected token 'x', "x" is not valid JSON}, {@code Unexpected end of JSON input}).
   *
   * @param text the decoded body
   * @return the raw JSON value
   */
  static Object parseJson(String text) {
    if (text.isEmpty()) {
      return new LinkedHashMap<String, Object>();
    }
    int first = 0;
    while (first < text.length() && " \t\n\r".indexOf(text.charAt(first)) >= 0) {
      first++;
    }
    if (first == text.length()) {
      throw new SyntaxError("Unexpected end of JSON input", null);
    }
    char c = text.charAt(first);
    if (c != '{' && c != '[') {
      throw new SyntaxError(unexpectedToken(text, first), null);
    }
    try {
      return JSON_READER.readValue(text);
    } catch (JsonEOFException e) {
      throw new SyntaxError("Unexpected end of JSON input", e);
    } catch (JsonProcessingException e) {
      JsonLocation location = e.getLocation();
      long offset = location == null ? -1 : location.getCharOffset();
      int position = offset < 0 ? first : (int) Math.min(Math.max(offset - 1, 0), text.length() - 1);
      throw new SyntaxError(unexpectedToken(text, position), e);
    }
  }

  /** V8's {@code kJsonParseUnexpectedToken*} messages: the offending character and the source around it. */
  private static String unexpectedToken(String text, int position) {
    String token = String.valueOf(text.charAt(position));
    String context;
    if (text.length() <= MAX_CONTEXT_CHARACTERS * 2) {
      context = "\"" + text + "\"";
    } else {
      int start = Math.max(0, position - MAX_CONTEXT_CHARACTERS);
      int end = Math.min(text.length(), position + MAX_CONTEXT_CHARACTERS);
      context = (start > 0 ? "...\"" : "\"") + text.substring(start, end) + (end < text.length() ? "\"..." : "\"");
    }
    return "Unexpected token '" + token + "', " + context + " is not valid JSON";
  }
}
