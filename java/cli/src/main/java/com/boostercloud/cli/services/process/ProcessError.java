package com.boostercloud.cli.services.process;

import com.boostercloud.cli.common.CliException;

/** A failed process operation ({@code ProcessError} in TypeScript, a tagged wrapper of an error). */
public final class ProcessError extends CliException {
  private static final long serialVersionUID = 1L;

  /**
   * Wraps an error.
   *
   * @param error the underlying error
   */
  public ProcessError(Throwable error) {
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
    return "ProcessError";
  }
}
