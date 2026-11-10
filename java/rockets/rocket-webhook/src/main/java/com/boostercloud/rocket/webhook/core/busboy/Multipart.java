package com.boostercloud.rocket.webhook.core.busboy;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of {@code busboy@1.6.0/lib/types/multipart.js}: a streaming {@code multipart/form-data} parser. Bytes are
 * {@linkplain #write written} as they arrive and the {@link Listener} is told about every field and file; {@link #end()}
 * finishes the form. Busboy's behaviour is kept:
 *
 * <ul>
 *   <li>a part is a <b>file</b> when its {@code Content-Disposition} has a {@code filename} (or {@code filename*})
 *       parameter or its type is {@code application/octet-stream}; otherwise it is a <b>field</b>, whose value is
 *       decoded with the part's charset ({@code utf8} by default);</li>
 *   <li>parts without {@code Content-Disposition: form-data} are skipped; defaults are type {@code text/plain},
 *       transfer encoding {@code 7bit}, no name;</li>
 *   <li>file names lose their directories unless {@code preservePath}; part header parameters that are not extended
 *       ones are decoded with {@code defParamCharset} (kept as Latin-1 characters when absent);</li>
 *   <li>limits: a file is cut at {@code fileSize} bytes ({@link FileListener#limit()}), a field value at
 *       {@code fieldSize} (1 MiB by default, {@code valueTruncated}); the files, fields and parts above
 *       {@code files}/{@code fields}/{@code parts} are skipped (the listener's {@code *Limit} methods are called
 *       once);</li>
 *   <li>the preamble before the first boundary is ignored, and so is what is written after the closing boundary
 *       (Busboy still parses the rest of the chunk that contains it).</li>
 * </ul>
 *
 * <p>Errors are Busboy's {@code 'error'} events, reported to {@link Listener#error} as {@link IllegalStateException}s
 * with Busboy's messages; the default listener method throws them. {@code Malformed part header} (a malformed or
 * oversized part header) is reported while writing and, as in Busboy, the parser goes on with the next part;
 * {@code Unexpected end of form} ({@link #end()} before the closing boundary) also fails the open file
 * ({@link FileListener#error}) and stops the parser.
 */
public final class Multipart {

  /** Bytes Busboy writes before the body, in case there is no preamble. */
  private static final byte[] BUF_CRLF = {'\r', '\n'};
  private static final byte[] BUF_CR = {'\r'};
  private static final byte[] BUF_DASH = {'-'};

  /** Busboy's default {@code limits.fieldSize}: 1 MiB. */
  public static final long DEFAULT_FIELD_SIZE_LIMIT = 1024 * 1024;

  /** What Busboy reports. */
  public interface Listener {
    /**
     * A file part starts ({@code 'file'} event).
     *
     * @param name the part name, or {@code null}
     * @param info the file name, transfer encoding and mime type
     * @return the consumer of the file content
     */
    FileListener file(String name, FileInfo info);

    /**
     * A field part ended ({@code 'field'} event).
     *
     * @param name the part name, or {@code null}
     * @param value the decoded value, or {@code null} when the charset is not supported
     * @param info the truncation flags, transfer encoding and mime type
     */
    void field(String name, String value, FieldInfo info);

    /** {@code 'partsLimit'}: the {@code parts} limit was reached. */
    default void partsLimit() {}

    /** {@code 'filesLimit'}: a file part was skipped because of the {@code files} limit. */
    default void filesLimit() {}

    /** {@code 'fieldsLimit'}: a field part was skipped because of the {@code fields} limit. */
    default void fieldsLimit() {}

    /**
     * {@code 'error'}. Busboy streams without an {@code 'error'} listener fail (Node throws the error), which is what
     * this default does.
     *
     * @param error the error
     */
    default void error(IllegalStateException error) {
      throw error;
    }

    /**
     * @return whether there is a {@code 'file'} listener ({@code listenerCount('file') > 0}): file parts are skipped
     *     otherwise
     */
    default boolean listensToFiles() {
      return true;
    }

    /**
     * @return whether there is a {@code 'field'} listener: field parts are skipped otherwise
     */
    default boolean listensToFields() {
      return true;
    }
  }

  /** The consumer of a file part (Busboy's {@code FileStream}). */
  public interface FileListener {
    /**
     * @param bytes content bytes (only valid during the call)
     * @param offset the first byte
     * @param length how many bytes
     */
    void data(byte[] bytes, int offset, int length);

    /** {@code 'limit'}: the file reached {@code limits.fileSize}; the rest of its content is discarded. */
    default void limit() {}

    /** The file part ended ({@code push(null)}, followed by the stream's {@code 'end'}). */
    void end();

    /**
     * The form ended in the middle of this file (the file stream is destroyed with the error).
     *
     * @param error {@code Unexpected end of form} or {@code Unexpected end of file}
     */
    default void error(IllegalStateException error) {}
  }

  /**
   * The {@code info} of a {@code 'file'} event.
   *
   * @param filename the file name, or {@code null} for an {@code application/octet-stream} part without one
   * @param encoding the {@code Content-Transfer-Encoding}, lower-cased
   * @param mimeType the {@code type/subtype} of the part
   */
  public record FileInfo(String filename, String encoding, String mimeType) {}

  /**
   * The {@code info} of a {@code 'field'} event.
   *
   * @param nameTruncated always {@code false}
   * @param valueTruncated whether the value was cut at {@code fieldSize}
   * @param encoding the {@code Content-Transfer-Encoding}, lower-cased
   * @param mimeType the {@code type/subtype} of the part
   */
  public record FieldInfo(boolean nameTruncated, boolean valueTruncated, String encoding, String mimeType) {}

  /**
   * The parser configuration (Busboy's {@code instanceCfg}). {@code null} limits are unlimited ({@code Infinity}),
   * except {@code fieldSize} (1 MiB).
   *
   * @param boundary the boundary ({@code conType.params.boundary})
   * @param defCharset the charset of field values without one ({@code utf8} when {@code null} or empty)
   * @param defParamCharset the charset of non-extended part header parameters, or {@code null}
   * @param preservePath whether file names keep their directories
   * @param fieldSize the maximum field value size in bytes, or {@code null}
   * @param fileSize the maximum file size in bytes, or {@code null}
   * @param files the maximum number of file parts, or {@code null}
   * @param fields the maximum number of field parts, or {@code null}
   * @param parts the maximum number of parts, or {@code null}
   */
  public record Config(String boundary, String defCharset, String defParamCharset, boolean preservePath,
      Long fieldSize, Long fileSize, Long files, Long fields, Long parts) {}

  private final Listener listener;
  private final HeaderParser hparser;
  private final BusboyUtils.Decoder paramDecoder;
  private final String defCharset;
  private final boolean preservePath;
  private final long fieldSizeLimit;
  private final long fileSizeLimit;
  private final long filesLimit;
  private final long fieldsLimit;
  private final long partsLimit;

  private StreamSearch bparser;
  private boolean ignoring;
  private HeaderParser activeHeaderParser;
  private FileListener fileStream;
  private boolean complete;
  private boolean ended;

  private long parts = -1; // Account for initial boundary
  private long fields;
  private long files;
  private boolean skipPart;
  private long fileSize;

  private ByteArrayOutputStream field;
  private int fieldChunks;
  private long fieldSize;
  private String partCharset;
  private String partEncoding;
  private String partType;
  private String partName;
  private boolean partTruncated;

  private boolean hitFilesLimit;
  private boolean hitFieldsLimit;
  private int matchPostBoundary;

  /**
   * @param cfg the configuration
   * @param listener the listener
   * @throws IllegalStateException {@code Multipart: Boundary not found} when the boundary is {@code null}
   */
  public Multipart(Config cfg, Listener listener) {
    if (cfg.boundary() == null) {
      throw new IllegalStateException("Multipart: Boundary not found");
    }
    this.listener = listener;
    String boundary = cfg.boundary();
    this.paramDecoder = cfg.defParamCharset() != null && !cfg.defParamCharset().isEmpty()
        ? BusboyUtils.getDecoder(cfg.defParamCharset())
        : BusboyUtils.NULL_DECODER;
    this.defCharset = cfg.defCharset() == null || cfg.defCharset().isEmpty() ? "utf8" : cfg.defCharset();
    this.preservePath = cfg.preservePath();
    this.fieldSizeLimit = cfg.fieldSize() != null ? cfg.fieldSize() : DEFAULT_FIELD_SIZE_LIMIT;
    this.fileSizeLimit = cfg.fileSize() != null ? cfg.fileSize() : Long.MAX_VALUE;
    this.filesLimit = cfg.files() != null ? cfg.files() : Long.MAX_VALUE;
    this.fieldsLimit = cfg.fields() != null ? cfg.fields() : Long.MAX_VALUE;
    this.partsLimit = cfg.parts() != null ? cfg.parts() : Long.MAX_VALUE;
    this.hparser = new HeaderParser(this::onHeader);
    this.bparser = new StreamSearch("\r\n--" + boundary, this::ssCb);
    // Just in case there is no preamble
    write(BUF_CRLF);
  }

  /**
   * @return whether the closing boundary was read
   */
  public boolean isComplete() {
    return complete;
  }

  /**
   * {@code _write(chunk)}: parses the next bytes of the body (ignored after {@link #end()}).
   *
   * @param chunk the bytes (the array is not kept)
   */
  public void write(byte[] chunk) {
    if (!ignoring) {
      bparser.push(chunk, 0);
    }
  }

  /**
   * Parses {@code length} bytes of {@code buffer}.
   *
   * @param buffer the buffer
   * @param length how many bytes, from the start
   */
  public void write(byte[] buffer, int length) {
    write(length == buffer.length ? buffer : Arrays.copyOf(buffer, length));
  }

  /**
   * {@code end()} ({@code _final}, then {@code _destroy}): flushes the look-behind data and checks the form ended,
   * reporting {@code Unexpected end of form} when the closing boundary was not read (also to the open file, if any),
   * {@code Malformed part header} when a part header is incomplete, and {@code Unexpected end of file} to a file that is
   * still open.
   */
  public void end() {
    if (ended) {
      return;
    }
    ended = true;
    if (!ignoring) {
      bparser.destroy();
    }
    IllegalStateException err = complete ? checkEndState() : new IllegalStateException("Unexpected end of form");
    activeHeaderParser = null;
    ignoring = true;
    if (err != null) {
      FileListener open = fileStream;
      fileStream = null;
      if (open != null) {
        open.error(err);
      }
      listener.error(err);
    }
  }

  private IllegalStateException checkEndState() {
    if (activeHeaderParser != null) {
      return new IllegalStateException("Malformed part header");
    }
    FileListener open = fileStream;
    if (open != null) {
      fileStream = null;
      open.error(new IllegalStateException("Unexpected end of file"));
    }
    return complete ? null : new IllegalStateException("Unexpected end of form");
  }

  private void onHeader(Map<String, List<String>> header) {
    activeHeaderParser = null;
    skipPart = false;

    partType = "text/plain";
    partCharset = defCharset;
    partEncoding = "7bit";
    partName = null;
    partTruncated = false;

    String filename = null;
    List<String> disposition = header.get("content-disposition");
    if (disposition == null) {
      skipPart = true;
      return;
    }

    BusboyUtils.Disposition disp = BusboyUtils.parseDisposition(disposition.getFirst(), paramDecoder);
    if (disp == null || !disp.type().equals("form-data")) {
      skipPart = true;
      return;
    }

    Map<String, String> params = disp.params();
    if (isTruthy(params.get("name"))) {
      partName = params.get("name");
    }
    if (isTruthy(params.get("filename*"))) {
      filename = params.get("filename*");
    } else if (isTruthy(params.get("filename"))) {
      filename = params.get("filename");
    }
    if (filename != null && !preservePath) {
      filename = BusboyUtils.basename(filename);
    }

    List<String> contentType = header.get("content-type");
    if (contentType != null && isTruthy(contentType.getFirst())) {
      BusboyUtils.ContentType conType = BusboyUtils.parseContentType(contentType.getFirst());
      if (conType != null) {
        partType = conType.type() + "/" + conType.subtype();
        String charset = conType.params().get("charset");
        if (charset != null) {
          partCharset = charset.toLowerCase(Locale.ROOT);
        }
      }
    }

    List<String> transferEncoding = header.get("content-transfer-encoding");
    if (transferEncoding != null) {
      partEncoding = transferEncoding.getFirst().toLowerCase(Locale.ROOT);
    }

    if (partType.equals("application/octet-stream") || filename != null) {
      // File
      if (files == filesLimit) {
        if (!hitFilesLimit) {
          hitFilesLimit = true;
          listener.filesLimit();
        }
        skipPart = true;
        return;
      }
      ++files;

      if (!listener.listensToFiles()) {
        skipPart = true;
        return;
      }

      fileSize = 0;
      fileStream = listener.file(partName, new FileInfo(filename, partEncoding, partType));
    } else {
      // Non-file
      if (fields == fieldsLimit) {
        if (!hitFieldsLimit) {
          hitFieldsLimit = true;
          listener.fieldsLimit();
        }
        skipPart = true;
        return;
      }
      ++fields;

      if (!listener.listensToFields()) {
        skipPart = true;
        return;
      }

      field = new ByteArrayOutputStream();
      fieldChunks = 0;
      fieldSize = 0;
    }
  }

  private static boolean isTruthy(String value) {
    return value != null && !value.isEmpty();
  }

  private void ssCb(boolean isMatch, byte[] data, int start, int end, boolean isDataSafe) {
    retrydata:
    while (data != null) {
      if (activeHeaderParser != null) {
        int ret = activeHeaderParser.push(data, start, end);
        if (ret == -1) {
          activeHeaderParser = null;
          hparser.reset();
          listener.error(new IllegalStateException("Malformed part header"));
          break;
        }
        start = ret;
      }

      if (start >= end) {
        break;
      }

      if (matchPostBoundary != 0) {
        if (matchPostBoundary == 1) {
          switch (data[start]) {
            case '-':
              // Try matching '--' after boundary
              matchPostBoundary = 2;
              ++start;
              break;
            case '\r':
              // Try matching CR LF before header
              matchPostBoundary = 3;
              ++start;
              break;
            default:
              matchPostBoundary = 0;
          }
          if (start == end) {
            return;
          }
        }

        if (matchPostBoundary == 2) {
          matchPostBoundary = 0;
          if (data[start] == '-') {
            // End of multipart data. As in Busboy, later writes are ignored, but the search goes on over the rest of
            // the current chunk (so a boundary in the epilogue of the same chunk starts a new part).
            complete = true;
            ignoring = true;
            return;
          }
          // We saw something other than '-', so put the dash we consumed "back"
          ssCb(false, BUF_DASH, 0, 1, false);
        } else if (matchPostBoundary == 3) {
          matchPostBoundary = 0;
          if (data[start] == '\n') {
            ++start;
            if (parts >= partsLimit) {
              break;
            }
            // Prepare the header parser
            activeHeaderParser = hparser;
            if (start == end) {
              break;
            }
            // Process the remaining data as a header
            continue retrydata;
          } else {
            // We saw something other than LF, so put the CR we consumed "back"
            ssCb(false, BUF_CR, 0, 1, false);
          }
        }
      }

      if (!skipPart) {
        if (fileStream != null) {
          int actualLen = (int) Math.max(0, Math.min(end - start, fileSizeLimit - fileSize));
          fileSize += actualLen;
          if (fileSize == fileSizeLimit) {
            if (actualLen > 0) {
              fileStream.data(data, start, actualLen);
            }
            fileStream.limit();
            skipPart = true;
          } else {
            fileStream.data(data, start, actualLen);
          }
        } else if (field != null) {
          int actualLen = (int) Math.max(0, Math.min(end - start, fieldSizeLimit - fieldSize));
          fieldSize += actualLen;
          field.write(data, start, actualLen);
          fieldChunks++;
          if (fieldSize == fieldSizeLimit) {
            skipPart = true;
            partTruncated = true;
          }
        }
      }

      break;
    }

    if (isMatch) {
      matchPostBoundary = 1;

      if (fileStream != null) {
        // End the active file stream if the previous part was a file
        FileListener ended = fileStream;
        fileStream = null;
        ended.end();
      } else if (field != null) {
        String value = fieldChunks == 0 ? "" : BusboyUtils.convertToUTF8(field.toByteArray(), partCharset);
        field = null;
        fieldSize = 0;
        listener.field(partName, value, new FieldInfo(false, partTruncated, partEncoding, partType));
      }

      if (++parts == partsLimit) {
        listener.partsLimit();
      }
    }
  }
}
