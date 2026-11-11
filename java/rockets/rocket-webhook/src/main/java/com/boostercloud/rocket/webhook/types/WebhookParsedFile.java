package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.Objects;

/**
 * Port of {@code WebhookParsedFile} ({@code webhook-multipart-form.ts}): a file part of a {@code multipart/form-data}
 * request (a part with a {@code filename} parameter or of type {@code application/octet-stream}), fully buffered.
 *
 * @param name the {@code name} parameter of the part's {@code Content-Disposition}, or {@code null} when absent
 * @param bufferFile the content (TypeScript {@code Buffer}); truncated at {@code limits.fileSize} when set
 * @param filename the {@code filename} (or {@code filename*}) parameter, without directories unless
 *     {@code preservePath}; {@code null} for an octet-stream part without file name
 * @param encoding the part's {@code Content-Transfer-Encoding}, lower-cased ({@code 7bit} by default)
 * @param mimeType the part's {@code Content-Type} type and subtype ({@code text/plain} by default)
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebhookParsedFile(String name, byte[] bufferFile, String filename, String encoding, String mimeType) {

  /** Canonical constructor: copies the content. */
  public WebhookParsedFile {
    bufferFile = bufferFile == null ? new byte[0] : bufferFile.clone();
  }

  /**
   * @return a copy of the content
   */
  @Override
  public byte[] bufferFile() {
    return bufferFile.clone();
  }

  /**
   * @return the content size in bytes
   */
  public int size() {
    return bufferFile.length;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof WebhookParsedFile file
        && Objects.equals(name, file.name)
        && Arrays.equals(bufferFile, file.bufferFile)
        && Objects.equals(filename, file.filename)
        && Objects.equals(encoding, file.encoding)
        && Objects.equals(mimeType, file.mimeType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, Arrays.hashCode(bufferFile), filename, encoding, mimeType);
  }

  @Override
  public String toString() {
    return "WebhookParsedFile[name=" + name + ", bufferFile=<" + bufferFile.length + " bytes>, filename=" + filename
        + ", encoding=" + encoding + ", mimeType=" + mimeType + "]";
  }
}
