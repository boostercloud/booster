package com.boostercloud.cli.services.filesystem;

import com.boostercloud.cli.common.CliException;

/** A failed file-system operation ({@code FileSystemError} in TypeScript, a tagged wrapper of an error). */
public final class FileSystemError extends CliException {
  private static final long serialVersionUID = 1L;

  /**
   * Wraps an error.
   *
   * @param error the underlying error
   */
  public FileSystemError(Throwable error) {
    super(error.getMessage(), error);
  }

  /**
   * The wrapped error ({@code error} property in TypeScript).
   *
   * @return the underlying error
   */
  public Throwable error() {
    return getCause();
  }

  @Override
  public String name() {
    return "FileSystemError";
  }
}
