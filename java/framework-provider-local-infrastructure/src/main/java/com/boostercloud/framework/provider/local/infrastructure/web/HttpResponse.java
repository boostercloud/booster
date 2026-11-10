package com.boostercloud.framework.provider.local.infrastructure.web;

import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An HTTP response with the semantics of the Express {@code Response} the TypeScript controllers and rockets use:
 * {@link #status(int)}, {@link #header(String, Object)} / {@link #setHeader(String, Object)}, {@link #json(Object)},
 * {@link #send(Object)}, {@link #download(Path)}, {@link #end()} and, for streaming, {@link #outputStream()}.
 *
 * <p>Headers are buffered until the response is committed (the first body write); setting one afterwards fails with
 * Node's message ({@code Cannot set headers after they are sent to the client}). {@link #json(Object)} serialises
 * with {@link BoosterJson} ({@code JSON.stringify}) and sends {@code application/json; charset=utf-8}; strings are
 * sent as UTF-8 ({@code text/html} unless a content type was set), byte arrays as {@code application/octet-stream};
 * a {@code HEAD} response gets the headers without the body.
 *
 * <p>The server creates a Jetty-backed instance for each request; {@link MemoryHttpResponse} records a response in
 * memory (tests). Implementations provide {@link #writeComplete} and {@link #openStream}.
 */
public abstract class HttpResponse {

  private static final byte[] EMPTY = new byte[0];
  /** Node's {@code checkIsHttpToken}. */
  private static final Pattern HTTP_TOKEN = Pattern.compile("^[\\^_`a-zA-Z\\-0-9!#$%&'*+.|~]+$");
  /** Node's {@code checkInvalidHeaderChar}. */
  private static final Pattern INVALID_HEADER_CHAR = Pattern.compile("[^\\t\\x20-\\x7e\\x80-\\xff]");
  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

  private final boolean head;
  private final Map<String, HeaderValues> headers = new LinkedHashMap<>();
  private int statusCode = 200;
  private boolean headersSent;
  private boolean finished;
  private OutputStream stream;

  private record HeaderValues(String name, List<String> values) {}

  /**
   * @param head whether this answers a {@code HEAD} request (the body is not sent)
   */
  protected HttpResponse(boolean head) {
    this.head = head;
  }

  /**
   * Sends the status, the headers and a complete body, and finishes the response.
   *
   * @param status the status
   * @param headerList the headers, in order
   * @param body the body (empty for {@code HEAD})
   */
  protected abstract void writeComplete(int status, List<Map.Entry<String, String>> headerList, byte[] body);

  /**
   * Sends the status and the headers and opens the body; closing the stream finishes the response.
   *
   * @param status the status
   * @param headerList the headers, in order
   * @return the body stream
   */
  protected abstract OutputStream openStream(int status, List<Map.Entry<String, String>> headerList);

  /**
   * {@code res.status(code)}.
   *
   * @param code the status code
   * @return this response
   * @throws IllegalArgumentException {@code Invalid status code: <code>} outside 100-999 (Node's {@code RangeError})
   */
  public synchronized HttpResponse status(int code) {
    if (code < 100 || code > 999) {
      throw new IllegalArgumentException("Invalid status code: " + code);
    }
    this.statusCode = code;
    return this;
  }

  /**
   * @return the status code ({@code res.statusCode}, 200 by default)
   */
  public synchronized int statusCode() {
    return statusCode;
  }

  /**
   * Node's {@code res.setHeader(name, value)}: replaces the header. As Node ({@code validateHeaderName} and
   * {@code validateHeaderValue}), the name must be an HTTP token and the value may only contain tabs and the
   * characters {@code U+0020}-{@code U+007E} and {@code U+0080}-{@code U+00FF} (no CR, LF or other controls).
   *
   * @param name the header name (case kept, matched case-insensitively)
   * @param value a string, a number or a collection of strings
   * @return this response
   * @throws IllegalStateException when the headers were already sent
   * @throws IllegalArgumentException {@code Header name must be a valid HTTP token ["<name>"]} or
   *     {@code Invalid character in header content ["<name>"]} (Node's {@code TypeError}s)
   */
  public synchronized HttpResponse setHeader(String name, Object value) {
    ensureHeadersNotSent();
    if (name == null || !HTTP_TOKEN.matcher(name).matches()) {
      throw new IllegalArgumentException("Header name must be a valid HTTP token [\"" + name + "\"]");
    }
    List<String> values = new ArrayList<>();
    if (value instanceof Collection<?> collection) {
      collection.forEach(element -> values.add(String.valueOf(element)));
    } else if (value != null) {
      values.add(value instanceof Double number ? BoosterJson.formatNumber(number) : String.valueOf(value));
    }
    for (String text : values) {
      if (INVALID_HEADER_CHAR.matcher(text).find()) {
        throw new IllegalArgumentException("Invalid character in header content [\"" + name + "\"]");
      }
    }
    headers.put(name.toLowerCase(Locale.ROOT), new HeaderValues(name, values));
    return this;
  }

  /**
   * Express's {@code res.header(name, value)} / {@code res.set}: like {@link #setHeader(String, Object)}, but a
   * {@code Content-Type} without charset gets {@code ; charset=utf-8} when its type has a default charset (text,
   * JSON, JavaScript).
   *
   * @param name the header name
   * @param value the value
   * @return this response
   */
  public HttpResponse header(String name, Object value) {
    if (name.equalsIgnoreCase("content-type") && value instanceof String type) {
      return setHeader(name, MimeTypes.withDefaultCharset(type));
    }
    return setHeader(name, value);
  }

  /**
   * Express's {@code res.type(type)}: sets the content type from a mime type or an extension ({@code "json"}).
   *
   * @param type the type
   * @return this response
   */
  public HttpResponse type(String type) {
    return header("Content-Type", MimeTypes.lookup(type));
  }

  /**
   * Adds a field to the {@code Vary} header unless it is already listed (the {@code vary} package).
   *
   * @param field the request header name
   * @return this response
   */
  public synchronized HttpResponse vary(String field) {
    String existing = getHeader("Vary");
    if (existing == null || existing.isBlank()) {
      return setHeader("Vary", field);
    }
    for (String listed : existing.split(",")) {
      if (listed.trim().equalsIgnoreCase(field) || listed.trim().equals("*")) {
        return this;
      }
    }
    return setHeader("Vary", existing + ", " + field);
  }

  /**
   * @param name a header name (any case)
   * @return its values joined with {@code ", "}, or {@code null}
   */
  public synchronized String getHeader(String name) {
    HeaderValues values = headers.get(name.toLowerCase(Locale.ROOT));
    return values == null ? null : String.join(", ", values.values());
  }

  /**
   * @param name a header name (any case)
   * @return its values (empty when absent)
   */
  public synchronized List<String> getHeaderValues(String name) {
    HeaderValues values = headers.get(name.toLowerCase(Locale.ROOT));
    return values == null ? List.of() : List.copyOf(values.values());
  }

  /**
   * @param name a header name (any case)
   * @return this response
   */
  public synchronized HttpResponse removeHeader(String name) {
    ensureHeadersNotSent();
    headers.remove(name.toLowerCase(Locale.ROOT));
    return this;
  }

  /**
   * @return the headers set so far, with their original names
   */
  public synchronized Map<String, List<String>> headers() {
    Map<String, List<String>> copy = new LinkedHashMap<>();
    headers.values().forEach(values -> copy.put(values.name(), List.copyOf(values.values())));
    return copy;
  }

  /**
   * @return whether the status and headers were sent ({@code res.headersSent})
   */
  public synchronized boolean headersSent() {
    return headersSent;
  }

  /**
   * @return whether the response is complete ({@code res.writableEnded})
   */
  public synchronized boolean finished() {
    return finished;
  }

  /**
   * @return whether this answers a {@code HEAD} request
   */
  public boolean isHead() {
    return head;
  }

  /**
   * {@code res.json(body)}: sends {@code JSON.stringify(body)}.
   *
   * @param body any JSON-serialisable value ({@code null} sends {@code null})
   */
  public void json(Object body) {
    String text = BoosterJson.toJson(body);
    if (!hasContentType()) {
      header("Content-Type", "application/json");
    }
    send(text);
  }

  /**
   * {@code res.send(body)}: a string is sent as UTF-8 ({@code text/html} when the content type is absent or empty, the
   * charset forced to {@code utf-8}); a byte array as is ({@code application/octet-stream} when absent or empty); {@code null} as an empty body;
   * anything else as JSON. 204 and 304 responses drop the body and its headers, 205 sends an empty body.
   *
   * @param body the body
   */
  public void send(Object body) {
    byte[] bytes;
    if (body == null) {
      bytes = EMPTY;
    } else if (body instanceof String text) {
      if (!hasContentType()) {
        type("html");
      }
      setHeader("Content-Type", MimeTypes.setCharset(getHeader("Content-Type"), "utf-8"));
      bytes = text.getBytes(StandardCharsets.UTF_8);
    } else if (body instanceof byte[] array) {
      if (!hasContentType()) {
        type("bin");
      }
      bytes = array;
    } else {
      json(body);
      return;
    }
    int status = statusCode();
    if (status == 204 || status == 304) {
      removeHeader("Content-Type");
      removeHeader("Content-Length");
      removeHeader("Transfer-Encoding");
      bytes = EMPTY;
    } else if (status == 205) {
      setHeader("Content-Length", "0");
      removeHeader("Transfer-Encoding");
      bytes = EMPTY;
    } else {
      setHeader("Content-Length", Integer.toString(bytes.length));
    }
    finishWith(bytes);
  }

  /**
   * Express's {@code if (!this.get('Content-Type'))}: an absent <b>or empty</b> content type counts as missing.
   *
   * @return whether a non-empty {@code Content-Type} is set
   */
  private boolean hasContentType() {
    String type = getHeader("Content-Type");
    return type != null && !type.isEmpty();
  }

  /**
   * {@code res.end()}: finishes the response (an empty body unless a stream was opened).
   */
  public void end() {
    OutputStream open;
    synchronized (this) {
      if (finished) {
        return;
      }
      open = stream;
    }
    if (open != null) {
      try {
        open.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return;
    }
    synchronized (this) {
      if (getHeader("Content-Length") == null && getHeader("Transfer-Encoding") == null && statusCode != 204
          && statusCode != 304) {
        setHeader("Content-Length", "0");
      }
    }
    finishWith(EMPTY);
  }

  /**
   * The response body as a stream (TypeScript {@code res.write}/{@code pipe}): the status and headers are sent on
   * the first write; {@link OutputStream#close()} or {@link #end()} finishes the response. For {@code HEAD} requests
   * the bytes are discarded.
   *
   * @return the stream (the same one on every call)
   */
  public synchronized OutputStream outputStream() {
    if (stream == null) {
      if (finished) {
        throw new IllegalStateException("The response has already been sent");
      }
      stream = new CommittingStream();
    }
    return stream;
  }

  /**
   * {@code res.download(path)}: sends a file as an attachment named after it.
   *
   * @param file the file
   */
  public void download(Path file) {
    download(file, file.getFileName().toString());
  }

  /**
   * {@code res.download(path, filename)}: sends a file with {@code Content-Disposition: attachment; filename="..."},
   * a content type from its extension, {@code Content-Length}, {@code Last-Modified}, a weak {@code ETag},
   * {@code Accept-Ranges: bytes} and {@code Cache-Control: public, max-age=0} (the {@code send} package defaults).
   *
   * @param file the file
   * @param fileName the name the client should save it as
   * @throws UncheckedIOException with Node's message ({@code ENOENT: no such file or directory, stat '<path>'}) when
   *     the file does not exist, or when it cannot be read
   */
  public void download(Path file, String fileName) {
    Path absolute = file.toAbsolutePath();
    BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(absolute, BasicFileAttributes.class);
    } catch (NoSuchFileException e) {
      throw new UncheckedIOException("ENOENT: no such file or directory, stat '" + absolute + "'", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e.getMessage(), e);
    }
    if (attributes.isDirectory()) {
      throw new UncheckedIOException("EISDIR: illegal operation on a directory, read",
          new IOException("Is a directory: " + absolute));
    }
    long modified = attributes.lastModifiedTime().toMillis();
    setHeader("Content-Disposition", contentDisposition(fileName));
    setHeader("Accept-Ranges", "bytes");
    setHeader("Cache-Control", "public, max-age=0");
    setHeader("Last-Modified", HTTP_DATE.format(attributes.lastModifiedTime().toInstant()));
    setHeader("ETag", "W/\"" + Long.toHexString(attributes.size()) + "-" + Long.toHexString(modified) + "\"");
    if (getHeader("Content-Type") == null) {
      setHeader("Content-Type", MimeTypes.forFileName(fileName));
    }
    setHeader("Content-Length", Long.toString(attributes.size()));
    try (OutputStream out = outputStream()) {
      if (!head) {
        Files.copy(absolute, out);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e.getMessage(), e);
    }
  }

  /**
   * The {@code content-disposition} package: {@code attachment; filename="name"}, plus {@code filename*} for names
   * that are not ISO-8859-1.
   *
   * @param fileName the file name
   * @return the header value
   */
  public static String contentDisposition(String fileName) {
    String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
    boolean latin1 = name.chars().allMatch(c -> c >= 0x20 && c <= 0xFF && c != 0x7F);
    String fallback = latin1 ? name : name.replaceAll("[^\\x20-\\x7e\\xa0-\\xff]", "?");
    String header = "attachment; filename=\"" + fallback.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    if (!latin1) {
      StringBuilder encoded = new StringBuilder();
      for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
        int c = b & 0xFF;
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "!#$&+-.^_`|~".indexOf(c) >= 0) {
          encoded.append((char) c);
        } else {
          encoded.append('%').append(String.format(Locale.ROOT, "%02X", c));
        }
      }
      header += "; filename*=UTF-8''" + encoded;
    }
    return header;
  }

  private void ensureHeadersNotSent() {
    if (headersSent) {
      throw new IllegalStateException("Cannot set headers after they are sent to the client");
    }
  }

  private List<Map.Entry<String, String>> headerList() {
    List<Map.Entry<String, String>> list = new ArrayList<>();
    headers.values().forEach(values -> values.values().forEach(value -> list.add(Map.entry(values.name(), value))));
    return list;
  }

  private void finishWith(byte[] bytes) {
    int status;
    List<Map.Entry<String, String>> list;
    synchronized (this) {
      if (finished || headersSent) {
        throw new IllegalStateException("Cannot set headers after they are sent to the client");
      }
      headersSent = true;
      finished = true;
      status = statusCode;
      list = headerList();
    }
    writeComplete(status, list, head ? EMPTY : bytes);
  }

  /** Sends the head on the first write and finishes the response on close. */
  private final class CommittingStream extends OutputStream {
    private OutputStream target;
    private boolean closed;

    private OutputStream target() {
      synchronized (HttpResponse.this) {
        if (target == null) {
          headersSent = true;
          target = openStream(statusCode, headerList());
        }
        return target;
      }
    }

    @Override
    public void write(int b) throws IOException {
      if (!head) {
        target().write(b);
      } else {
        target();
      }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      if (!head) {
        target().write(bytes, offset, length);
      } else {
        target();
      }
    }

    @Override
    public void flush() throws IOException {
      target().flush();
    }

    @Override
    public void close() throws IOException {
      synchronized (HttpResponse.this) {
        if (closed) {
          return;
        }
        closed = true;
        finished = true;
      }
      target().close();
    }
  }
}
