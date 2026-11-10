package com.boostercloud.cli.common;

/**
 * Base unchecked exception for every error raised by the CLI.
 *
 * <p>TypeScript throws plain {@code Error} objects whose {@code name} is {@code "Error"}; this class plays the
 * same role. {@link #name()} is what the error-handling code (for example {@link Script#catchError}) compares
 * against, mirroring {@code error.name} in TypeScript.
 */
public class CliException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a message.
   *
   * @param message the error message
   */
  public CliException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and the error that caused it.
   *
   * @param message the error message
   * @param cause the original error
   */
  public CliException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * The TypeScript-style error name ({@code "Error"} unless a subclass says otherwise).
   *
   * @return the name used when formatting stack traces and selecting error handlers
   */
  public String name() {
    return "Error";
  }
}
