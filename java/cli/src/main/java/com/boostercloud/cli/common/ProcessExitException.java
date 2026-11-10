package com.boostercloud.cli.common;

/**
 * Raised when a child process launched by the CLI (the user's application) finishes with a non-zero exit code.
 *
 * <p>The CLI propagates {@link #exitCode()} as its own exit code, wherever this exception appears in the cause
 * chain of the error that ends a command.
 */
public class ProcessExitException extends CliException {
  private static final long serialVersionUID = 1L;

  private final int exitCode;

  /**
   * Creates the exception.
   *
   * @param message the error message
   * @param exitCode the exit code of the child process
   */
  public ProcessExitException(String message, int exitCode) {
    super(message);
    this.exitCode = exitCode;
  }

  /**
   * The exit code of the child process.
   *
   * @return a non-zero exit code
   */
  public int exitCode() {
    return exitCode;
  }

  /**
   * Finds the exit code to use for an error: the one of the first {@link ProcessExitException} in the cause chain,
   * or {@code 1} when there is none.
   *
   * @param error the error that ended a command
   * @return the process exit code the CLI should finish with
   */
  public static int exitCodeOf(Throwable error) {
    Throwable current = error;
    int guard = 0;
    while (current != null && guard++ < 64) {
      if (current instanceof ProcessExitException exit) {
        return exit.exitCode();
      }
      current = current.getCause();
    }
    return 1;
  }
}
