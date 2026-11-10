package com.boostercloud.rocket.webhook.core.busboy;

import java.util.Map;

/**
 * Port of {@code busboy@1.6.0/lib/index.js}: {@code Busboy({ headers, ...options })} creates the parser for the
 * request's {@code Content-Type}. Only the {@code multipart/form-data} type is ported (Busboy's
 * {@code application/x-www-form-urlencoded} type is not: the webhook rocket only runs Busboy for content types that
 * contain {@code multipart/form-data}).
 */
public final class Busboy {

  private Busboy() {}

  /**
   * The Busboy options the webhook rocket can pass ({@code MultiPartConfig} plus Busboy's other limits).
   *
   * @param defCharset the charset of field values without one ({@code utf8} when {@code null})
   * @param defParamCharset the charset of non-extended part header parameters ({@code latin1}-like when
   *     {@code null})
   * @param preservePath whether file names keep their directories ({@code false} when {@code null})
   * @param fieldSize the maximum field value size, or {@code null} for 1 MiB
   * @param fileSize the maximum file size, or {@code null} for no limit
   * @param files the maximum number of files, or {@code null} for no limit
   * @param fields the maximum number of fields, or {@code null} for no limit
   * @param parts the maximum number of parts, or {@code null} for no limit
   */
  public record Options(String defCharset, String defParamCharset, Boolean preservePath, Long fieldSize, Long fileSize,
      Long files, Long fields, Long parts) {

    /** Busboy's defaults. */
    public static final Options DEFAULTS = new Options(null, null, null, null, null, null, null, null);
  }

  /**
   * {@code Busboy(cfg)}.
   *
   * @param headers the request headers (lower-cased names)
   * @param options the options
   * @param listener receives the fields and files
   * @return the parser
   * @throws IllegalStateException {@code Missing Content-Type} without a {@code content-type} header,
   *     {@code Malformed content type} when it cannot be parsed, {@code Unsupported content type: <type>} for other
   *     types than {@code multipart/form-data}, {@code Multipart: Boundary not found} without {@code boundary}
   */
  public static Multipart create(Map<String, String> headers, Options options, Multipart.Listener listener) {
    if (headers == null || headers.get("content-type") == null) {
      throw new IllegalStateException("Missing Content-Type");
    }
    Options cfg = options == null ? Options.DEFAULTS : options;
    String contentType = headers.get("content-type");
    BusboyUtils.ContentType conType = BusboyUtils.parseContentType(contentType);
    if (conType == null) {
      throw new IllegalStateException("Malformed content type");
    }
    if (conType.type().equals("multipart") && conType.subtype().equals("form-data")) {
      return new Multipart(new Multipart.Config(conType.params().get("boundary"), cfg.defCharset(),
          cfg.defParamCharset(), Boolean.TRUE.equals(cfg.preservePath()), cfg.fieldSize(), cfg.fileSize(), cfg.files(),
          cfg.fields(), cfg.parts()), listener);
    }
    throw new IllegalStateException("Unsupported content type: " + contentType);
  }
}
