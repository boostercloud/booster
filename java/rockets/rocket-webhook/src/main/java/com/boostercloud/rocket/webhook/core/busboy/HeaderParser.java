package com.boostercloud.rocket.webhook.core.busboy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of the {@code HeaderParser} of {@code busboy@1.6.0/lib/types/multipart.js}: parses the header block of a
 * multipart part, which may arrive split across chunks. Names are tokens (lower-cased in the result), values are kept
 * as Latin-1 text (one character per byte); folded values continue on lines starting with a space or a tab. The parse
 * fails (returns {@code -1}) on a malformed header, or when the block exceeds {@value #MAX_HEADER_SIZE} bytes; pairs
 * after the first {@value #MAX_HEADER_PAIRS} are dropped. When the empty line ending the block is read, the callback
 * receives the headers ({@code name -> values}, first occurrence first) and the parser resets.
 */
final class HeaderParser {

  /** The header pair limit (Node's). */
  static final int MAX_HEADER_PAIRS = 2000;
  /** The header block size limit (Node's default). */
  static final int MAX_HEADER_SIZE = 16 * 1024;

  private static final int HPARSER_NAME = 0;
  private static final int HPARSER_PRE_OWS = 1;
  private static final int HPARSER_VALUE = 2;

  /** RFC 7230 {@code tchar} (Busboy's {@code TOKEN} table). */
  private static final boolean[] TOKEN = BusboyUtils.TOKEN;
  /** {@code field-vchar}, tab and space (Busboy's {@code FIELD_VCHAR} table). */
  private static final boolean[] FIELD_VCHAR = fieldVchar();

  private final Consumer<Map<String, List<String>>> cb;
  private Map<String, List<String>> header;
  private int pairCount;
  private int byteCount;
  private int state;
  private StringBuilder name;
  private StringBuilder value;
  private int crlf;

  /**
   * @param cb receives the headers of each completed block
   */
  HeaderParser(Consumer<Map<String, List<String>>> cb) {
    this.cb = cb;
    reset();
  }

  private static boolean[] fieldVchar() {
    boolean[] table = new boolean[256];
    table['\t'] = true;
    for (int c = 0x20; c <= 0x7E; c++) {
      table[c] = true;
    }
    for (int c = 0x80; c <= 0xFF; c++) {
      table[c] = true;
    }
    return table;
  }

  /** Starts a new header block. */
  void reset() {
    header = new LinkedHashMap<>();
    pairCount = 0;
    byteCount = 0;
    state = HPARSER_NAME;
    name = new StringBuilder();
    value = new StringBuilder();
    crlf = 0;
  }

  private static void appendLatin1(StringBuilder target, byte[] chunk, int start, int end) {
    for (int i = start; i < end; i++) {
      target.append((char) (chunk[i] & 0xFF));
    }
  }

  /**
   * {@code push(chunk, pos, end)}.
   *
   * @param chunk the data
   * @param pos the first byte to parse
   * @param end the end (exclusive)
   * @return the position right after the header block when it ended in this chunk, {@code -1} when the header is
   *     malformed, else a position at or after {@code end} (Busboy may return {@code end + 1})
   */
  @SuppressWarnings("fallthrough")
  int push(byte[] chunk, int pos, int end) {
    int start = pos;
    while (pos < end) {
      switch (state) {
        case HPARSER_NAME: {
          boolean done = false;
          for (; pos < end; ++pos) {
            if (byteCount == MAX_HEADER_SIZE) {
              return -1;
            }
            ++byteCount;
            int code = chunk[pos] & 0xFF;
            if (!TOKEN[code]) {
              if (code != ':') {
                return -1;
              }
              appendLatin1(name, chunk, start, pos);
              if (name.isEmpty()) {
                return -1;
              }
              ++pos;
              done = true;
              state = HPARSER_PRE_OWS;
              break;
            }
          }
          if (!done) {
            appendLatin1(name, chunk, start, pos);
            break;
          }
        }
        // FALLTHROUGH
        case HPARSER_PRE_OWS: {
          // Skip optional whitespace
          boolean done = false;
          for (; pos < end; ++pos) {
            if (byteCount == MAX_HEADER_SIZE) {
              return -1;
            }
            ++byteCount;
            int code = chunk[pos] & 0xFF;
            if (code != ' ' && code != '\t') {
              start = pos;
              done = true;
              state = HPARSER_VALUE;
              break;
            }
          }
          if (!done) {
            break;
          }
        }
        // FALLTHROUGH
        case HPARSER_VALUE:
          switch (crlf) {
            case 0: // Nothing yet
              for (; pos < end; ++pos) {
                if (byteCount == MAX_HEADER_SIZE) {
                  return -1;
                }
                ++byteCount;
                int code = chunk[pos] & 0xFF;
                if (!FIELD_VCHAR[code]) {
                  if (code != '\r') {
                    return -1;
                  }
                  ++crlf;
                  break;
                }
              }
              appendLatin1(value, chunk, start, pos++);
              break;
            case 1: // Received CR
              if (byteCount == MAX_HEADER_SIZE) {
                return -1;
              }
              ++byteCount;
              if (chunk[pos++] != '\n') {
                return -1;
              }
              ++crlf;
              break;
            case 2: { // Received CR LF
              if (byteCount == MAX_HEADER_SIZE) {
                return -1;
              }
              ++byteCount;
              int code = chunk[pos] & 0xFF;
              if (code == ' ' || code == '\t') {
                // Folded value
                start = pos;
                crlf = 0;
              } else {
                if (++pairCount < MAX_HEADER_PAIRS) {
                  String headerName = name.toString().toLowerCase(Locale.ROOT);
                  header.computeIfAbsent(headerName, key -> new ArrayList<>()).add(value.toString());
                }
                if (code == '\r') {
                  ++crlf;
                  ++pos;
                } else {
                  // Assume start of next header field name
                  start = pos;
                  crlf = 0;
                  state = HPARSER_NAME;
                  name = new StringBuilder();
                  value = new StringBuilder();
                }
              }
              break;
            }
            case 3: { // Received CR LF CR
              if (byteCount == MAX_HEADER_SIZE) {
                return -1;
              }
              ++byteCount;
              if (chunk[pos++] != '\n') {
                return -1;
              }
              // End of header
              Map<String, List<String>> completed = header;
              reset();
              cb.accept(completed);
              return pos;
            }
            default:
              throw new IllegalStateException("Unexpected header parser state " + crlf);
          }
          break;
        default:
          throw new IllegalStateException("Unexpected header parser state " + state);
      }
    }
    return pos;
  }
}
