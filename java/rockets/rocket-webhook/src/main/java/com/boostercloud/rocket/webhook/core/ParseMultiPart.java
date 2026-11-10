package com.boostercloud.rocket.webhook.core;

import com.boostercloud.rocket.webhook.core.busboy.Busboy;
import com.boostercloud.rocket.webhook.core.busboy.Multipart;
import com.boostercloud.rocket.webhook.types.MultiPartConfig;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookMultipartForm;
import com.boostercloud.rocket.webhook.types.WebhookParsedField;
import com.boostercloud.rocket.webhook.types.WebhookParsedFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code parse-multi-part.ts}: parses a {@code multipart/form-data} request with Busboy
 * ({@link com.boostercloud.rocket.webhook.core.busboy}), buffering every file in memory.
 *
 * <p>The body comes from {@link WebhookHttpRequest#bodyStream()} when the transport left it unread (local provider,
 * TypeScript {@code request.pipe(bb)}), else from {@link WebhookHttpRequest#body()} (Azure: the bytes of the request,
 * TypeScript {@code bb.end(request.body)}).
 */
public final class ParseMultiPart {

  /** Size of the reads from the body stream. */
  static final int CHUNK_SIZE = 64 * 1024;

  private ParseMultiPart() {}

  /**
   * {@code parseMultipartFormData(request, multiPartConfig?)}.
   *
   * @param request the HTTP request (its {@code content-type} header carries the boundary)
   * @param multiPartConfig the Busboy options, or {@code null} for Busboy's defaults
   * @return the fields (in body order) and the files (in the order they end)
   * @throws IllegalStateException with Busboy's message when the content type or the body is malformed
   *     ({@code Malformed content type}, {@code Multipart: Boundary not found}, {@code Malformed part header},
   *     {@code Unexpected end of form}, ...)
   * @throws UncheckedIOException when reading the body stream fails
   */
  public static WebhookMultipartForm parseMultipartFormData(WebhookHttpRequest request, MultiPartConfig multiPartConfig) {
    List<WebhookParsedField> fields = new ArrayList<>();
    List<WebhookParsedFile> files = new ArrayList<>();

    Multipart bb = Busboy.create(request.headers(), options(multiPartConfig), new Multipart.Listener() {
      @Override
      public Multipart.FileListener file(String name, Multipart.FileInfo info) {
        ByteArrayOutputStream arrayBuffer = new ByteArrayOutputStream();
        return new Multipart.FileListener() {
          @Override
          public void data(byte[] bytes, int offset, int length) {
            arrayBuffer.write(bytes, offset, length);
          }

          @Override
          public void end() {
            files.add(new WebhookParsedFile(name, arrayBuffer.toByteArray(), info.filename(), info.encoding(),
                info.mimeType()));
          }
        };
      }

      @Override
      public void field(String name, String value, Multipart.FieldInfo info) {
        fields.add(new WebhookParsedField(name, value, info.nameTruncated(), info.valueTruncated(), info.encoding(),
            info.mimeType()));
      }
    });

    if (request.bodyStream() != null) {
      // Express requires to pipe the request
      try (InputStream stream = request.bodyStream().get()) {
        byte[] buffer = new byte[CHUNK_SIZE];
        int read;
        while ((read = stream.read(buffer)) != -1) {
          if (read > 0) {
            bb.write(buffer, read);
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      byte[] body = bodyBytes(request.body());
      if (body != null) {
        bb.write(body);
      }
    }
    bb.end();
    return new WebhookMultipartForm(fields, files);
  }

  private static byte[] bodyBytes(Object body) {
    return switch (body) {
      case null -> null;
      case byte[] bytes -> bytes;
      case String text -> text.getBytes(StandardCharsets.UTF_8);
      default -> throw new IllegalArgumentException("The \"chunk\" argument must be of type string or an instance of "
          + "Buffer, TypedArray, or DataView. Received an instance of " + body.getClass().getSimpleName());
    };
  }

  /**
   * @param config the webhook's multipart configuration
   * @return the Busboy options ({@code { headers, ...multiPartConfig }})
   */
  static Busboy.Options options(MultiPartConfig config) {
    if (config == null) {
      return Busboy.Options.DEFAULTS;
    }
    MultiPartConfig.Limits limits = config.limits();
    return new Busboy.Options(null, config.defParamCharset(), config.preservePath(), null,
        limits == null ? null : limits.fileSize(), limits == null ? null : limits.files(), null,
        limits == null ? null : limits.parts());
  }
}
