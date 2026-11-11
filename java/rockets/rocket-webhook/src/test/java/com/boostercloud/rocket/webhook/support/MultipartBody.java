package com.boostercloud.rocket.webhook.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Builds {@code multipart/form-data} bodies the way browsers and {@code curl -F} send them. */
public final class MultipartBody {

  /** The boundary of every body built here. */
  public static final String BOUNDARY = "----BoosterWebhookBoundary7MA4YWxkTrZu0gW";

  /** The {@code Content-Type} header of the bodies. */
  public static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  private MultipartBody() {}

  /**
   * @return an empty body
   */
  public static MultipartBody create() {
    return new MultipartBody();
  }

  /**
   * @param name the field name
   * @param value the value (UTF-8)
   * @return this body
   */
  public MultipartBody field(String name, String value) {
    text("--" + BOUNDARY + "\r\n");
    text("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
    text(value);
    text("\r\n");
    return this;
  }

  /**
   * @param name the field name
   * @param filename the file name
   * @param mimeType the part's content type
   * @param content the file bytes
   * @return this body
   */
  public MultipartBody file(String name, String filename, String mimeType, byte[] content) {
    text("--" + BOUNDARY + "\r\n");
    text("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
    text("Content-Type: " + mimeType + "\r\n\r\n");
    bytes.writeBytes(content);
    text("\r\n");
    return this;
  }

  /**
   * @return the body with its closing boundary
   */
  public byte[] build() {
    ByteArrayOutputStream copy = new ByteArrayOutputStream();
    copy.writeBytes(bytes.toByteArray());
    copy.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return copy.toByteArray();
  }

  /**
   * @return the body without the closing boundary (a truncated upload)
   */
  public byte[] buildTruncated() {
    return bytes.toByteArray();
  }

  private void text(String value) {
    bytes.writeBytes(value.getBytes(StandardCharsets.UTF_8));
  }
}
