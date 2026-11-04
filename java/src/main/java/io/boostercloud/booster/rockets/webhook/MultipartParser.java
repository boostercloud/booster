package io.boostercloud.booster.rockets.webhook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.*;
import java.util.*;

/** Byte-oriented, bounded parser: file contents are never decoded as text or written to a supplied filename. */
final class MultipartParser {
  private MultipartParser() {}

  static MultipartForm parse(byte[] body, String contentType, MultipartConfig config) {
    Map<String, String> type = parameters(contentType);
    String boundary = type.get("boundary");
    if (boundary == null || boundary.isEmpty() || boundary.length() > 70
        || !boundary.matches("[0-9A-Za-z'()+_,./:=? -]+") || boundary.endsWith(" "))
      throw invalid("Invalid multipart boundary");
    byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
    byte[] separator = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
    boolean beginsWithDelimiter = startsWith(body, 0, delimiter);
    int current = beginsWithDelimiter ? 0 : findDelimiter(body, separator, 0);
    if (current < 0) throw invalid("Missing multipart boundary");
    if (!beginsWithDelimiter) current += 2;
    current += delimiter.length;
    var fields = new ArrayList<MultipartForm.Field>();
    var files = new ArrayList<MultipartForm.File>();
    int parts = 0;
    while (true) {
      if (startsWith(body, current, new byte[] {'-', '-'})) {
        int end = current + 2;
        if (end != body.length && !startsWith(body, end, new byte[] {'\r', '\n'}))
          throw invalid("Invalid closing multipart boundary");
        return new MultipartForm(files, fields);
      }
      if (!startsWith(body, current, new byte[] {'\r', '\n'})) throw invalid("Malformed multipart delimiter");
      current += 2;
      if (++parts > config.maxParts()) throw tooLarge("Too many multipart parts");
      int headerEnd = indexOf(body, new byte[] {'\r', '\n', '\r', '\n'}, current);
      if (headerEnd < 0 || headerEnd - current > config.maxHeaderBytes()) throw invalid("Invalid multipart headers");
      var headers = headers(body, current, headerEnd);
      var disposition = parameters(headers.getOrDefault("content-disposition", ""));
      if (!"form-data".equalsIgnoreCase(disposition.get("")) || !disposition.containsKey("name"))
        throw invalid("Multipart form-data name is required");
      String name = decodeParameter(disposition.get("name"), config.parameterCharset());
      String filename = disposition.containsKey("filename*") ? extendedFilename(disposition.get("filename*"))
          : disposition.containsKey("filename") ? decodeParameter(disposition.get("filename"), config.parameterCharset()) : null;
      current = headerEnd + 4;
      int end = findDelimiter(body, separator, current);
      if (end < 0) throw invalid("Incomplete multipart body");
      String encoding = headers.getOrDefault("content-transfer-encoding", "7bit");
      String typeHeader = headers.getOrDefault("content-type", filename == null ? "text/plain" : "application/octet-stream");
      Map<String, String> media = parameters(typeHeader);
      String mediaType = media.get("").toLowerCase(Locale.ROOT);
      if (!mediaType.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+/[!#$%&'*+.^_`|~0-9A-Za-z-]+"))
        throw invalid("Invalid multipart content type");
      if (filename != null) {
        if (files.size() >= config.maxFiles()) throw tooLarge("Too many multipart files");
        if (end - current > config.maxFileBytes()) throw tooLarge("Multipart file is too large");
        if (!config.preservePath()) filename = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        files.add(new MultipartForm.File(name, Arrays.copyOfRange(body, current, end), filename, encoding, mediaType));
      } else {
        if (end - current > config.maxFieldBytes()) throw tooLarge("Multipart field is too large");
        Charset charset = StandardCharsets.UTF_8;
        if (media.containsKey("charset")) {
          try { charset = Charset.forName(media.get("charset")); }
          catch (IllegalArgumentException error) { throw invalid("Unsupported multipart charset"); }
        }
        fields.add(new MultipartForm.Field(name, new String(body, current, end - current, charset), false, false, encoding, mediaType));
      }
      current = end + separator.length;
    }
  }

  private static Map<String, String> headers(byte[] body, int start, int end) {
    var result = new LinkedHashMap<String, String>();
    String text = new String(body, start, end - start, StandardCharsets.ISO_8859_1);
    for (String line : text.split("\r\n", -1)) {
      int colon = line.indexOf(':');
      if (colon <= 0) throw invalid("Invalid multipart header");
      String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
      String value = line.substring(colon + 1).trim();
      if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0)
          || result.putIfAbsent(name, value) != null) throw invalid("Invalid or repeated multipart header");
    }
    return result;
  }

  static Map<String, String> parameters(String header) {
    var result = new LinkedHashMap<String, String>();
    int i = header.indexOf(';');
    result.put("", (i < 0 ? header : header.substring(0, i)).trim());
    if (i < 0) return result;
    while (i < header.length()) {
      i++;
      while (i < header.length() && Character.isWhitespace(header.charAt(i))) i++;
      if (i == header.length()) break;
      int nameStart = i;
      while (i < header.length() && header.charAt(i) != '=' && header.charAt(i) != ';') i++;
      if (i == header.length() || header.charAt(i) != '=') throw invalid("Invalid header parameter");
      String name = header.substring(nameStart, i).trim().toLowerCase(Locale.ROOT);
      if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw invalid("Invalid header parameter");
      i++;
      while (i < header.length() && Character.isWhitespace(header.charAt(i))) i++;
      var value = new StringBuilder();
      if (i < header.length() && header.charAt(i) == '"') {
        i++;
        boolean closed = false;
        while (i < header.length()) {
          char c = header.charAt(i++);
          if (c == '"') { closed = true; break; }
          if (c == '\\') {
            if (i == header.length()) throw invalid("Invalid quoted header parameter");
            char escaped = header.charAt(i++);
            // Preserve Windows path separators so preservePath=false can strip paths.
            if (escaped != '"' && escaped != '\\') value.append('\\');
            value.append(escaped);
          } else value.append(c);
        }
        if (!closed) throw invalid("Unclosed header parameter");
        while (i < header.length() && Character.isWhitespace(header.charAt(i))) i++;
        if (i < header.length() && header.charAt(i) != ';') throw invalid("Invalid header parameter suffix");
      } else {
        int start = i;
        while (i < header.length() && header.charAt(i) != ';') i++;
        value.append(header.substring(start, i).trim());
      }
      if (value.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0) || result.putIfAbsent(name, value.toString()) != null)
        throw invalid("Invalid or repeated header parameter");
    }
    return result;
  }

  private static String decodeParameter(String value, Charset charset) {
    return new String(value.getBytes(StandardCharsets.ISO_8859_1), charset);
  }
  private static String extendedFilename(String value) {
    int first = value.indexOf('\''); int second = value.indexOf('\'', first + 1);
    if (first < 1 || second < 0) throw invalid("Invalid extended filename");
    Charset charset;
    try { charset = Charset.forName(value.substring(0, first)); }
    catch (IllegalArgumentException error) { throw invalid("Unsupported filename charset"); }
    var bytes = new ByteArrayOutputStream();
    for (int i = second + 1; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '%') {
        if (i + 2 >= value.length()) throw invalid("Invalid filename encoding");
        int a = Character.digit(value.charAt(++i), 16), b = Character.digit(value.charAt(++i), 16);
        if (a < 0 || b < 0) throw invalid("Invalid filename encoding");
        bytes.write((a << 4) + b);
      } else {
        if (c > 127) throw invalid("Invalid extended filename");
        bytes.write(c);
      }
    }
    String decoded = bytes.toString(charset);
    if (decoded.chars().anyMatch(c -> c < 32 || c == 127)) throw invalid("Invalid filename");
    return decoded;
  }
  private static int findDelimiter(byte[] bytes, byte[] token, int start) {
    int found = start;
    while ((found = indexOf(bytes, token, found)) >= 0) {
      int next = found + token.length;
      if (startsWith(bytes, next, new byte[] {'-', '-'}) || startsWith(bytes, next, new byte[] {'\r', '\n'})) return found;
      found++;
    }
    return -1;
  }
  private static int indexOf(byte[] bytes, byte[] token, int start) {
    for (int i = start; i <= bytes.length - token.length; i++) if (startsWith(bytes, i, token)) return i;
    return -1;
  }
  private static boolean startsWith(byte[] bytes, int start, byte[] token) {
    if (start < 0 || start > bytes.length - token.length) return false;
    for (int i = 0; i < token.length; i++) if (bytes[start + i] != token[i]) return false;
    return true;
  }
  private static WebhookInputException invalid(String message) { return new WebhookInputException(400, message); }
  private static WebhookInputException tooLarge(String message) { return new WebhookInputException(413, message); }
}
