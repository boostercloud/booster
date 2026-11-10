package com.boostercloud.rocket.webhook.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Port of {@code MultiPartConfig} ({@code webhook-multipart-form.ts}): the subset of the Busboy options a webhook
 * can set for {@code multipart/form-data} requests. Every member is optional ({@code null} = Busboy's default).
 *
 * @param defParamCharset the character set of part header parameters that are not extended parameters
 *     ({@code filename}, {@code name}); {@code null} keeps the bytes as Latin-1 characters (Busboy's default)
 * @param preservePath whether file names keep their directories ({@code false} by default: only the base name)
 * @param limits size limits, or {@code null} for none
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record MultiPartConfig(String defParamCharset, Boolean preservePath, Limits limits) {

  /**
   * The {@code limits} of {@link MultiPartConfig}; {@code null} members are unlimited ({@code Infinity}). As with
   * Busboy, a file larger than {@code fileSize} is silently truncated and the files and parts above
   * {@code files}/{@code parts} are silently dropped.
   *
   * @param fileSize the maximum size of a file, in bytes
   * @param files the maximum number of file parts
   * @param parts the maximum number of parts (fields and files)
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Limits(Long fileSize, Long files, Long parts) {

    /**
     * @param fileSize the maximum size of a file, in bytes
     * @return limits with only {@code fileSize}
     */
    public static Limits fileSize(long fileSize) {
      return new Limits(fileSize, null, null);
    }

    /**
     * @param newFileSize the maximum file size in bytes, or {@code null}
     * @return a copy with that file size limit
     */
    public Limits withFileSize(Long newFileSize) {
      return new Limits(newFileSize, files, parts);
    }

    /**
     * @param newFiles the maximum number of files, or {@code null}
     * @return a copy with that file count limit
     */
    public Limits withFiles(Long newFiles) {
      return new Limits(fileSize, newFiles, parts);
    }

    /**
     * @param newParts the maximum number of parts, or {@code null}
     * @return a copy with that part count limit
     */
    public Limits withParts(Long newParts) {
      return new Limits(fileSize, files, newParts);
    }
  }

  /**
   * @param limits the limits
   * @return a configuration with only limits
   */
  public static MultiPartConfig of(Limits limits) {
    return new MultiPartConfig(null, null, limits);
  }
}
