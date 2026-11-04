package io.boostercloud.booster.rockets.webhook;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded equivalents of the webhook rocket's Busboy options. Limits reject, never silently truncate. */
public record MultipartConfig(Charset parameterCharset, boolean preservePath,
    int maxFileBytes, int maxFiles, int maxParts, int maxFieldBytes, int maxHeaderBytes) {
  public MultipartConfig {
    Objects.requireNonNull(parameterCharset, "parameterCharset");
    if (maxFileBytes < 0 || maxFiles < 0 || maxParts < 1 || maxFieldBytes < 0 || maxHeaderBytes < 1)
      throw new IllegalArgumentException("Multipart limits must be nonnegative and parts/header limits positive");
  }
  public static MultipartConfig defaults() {
    return new MultipartConfig(StandardCharsets.ISO_8859_1, false, 1_048_576, 16, 64, 65_536, 16_384);
  }
}
