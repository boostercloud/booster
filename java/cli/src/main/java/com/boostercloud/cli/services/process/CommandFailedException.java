package com.boostercloud.cli.services.process;

import com.boostercloud.cli.common.CliException;

/**
 * The error of a command that could not be started or finished with a non-zero exit code (the execa error of
 * TypeScript). Unlike execa 2, the message also contains the captured output, so that build errors reach
 * {@code errors.log} and {@code --verbose}.
 */
public final class CommandFailedException extends CliException {
  private static final long serialVersionUID = 1L;

  private final String command;
  private final int exitCode;
  private final String stdout;
  private final String stderr;

  /**
   * Creates the error.
   *
   * @param message the error message
   * @param command the command line
   * @param exitCode the exit code, or {@code -1} when the command could not be started
   * @param stdout the captured standard output
   * @param stderr the captured standard error
   * @param cause the underlying error, if any
   */
  public CommandFailedException(
      String message, String command, int exitCode, String stdout, String stderr, Throwable cause) {
    super(message, cause);
    this.command = command;
    this.exitCode = exitCode;
    this.stdout = stdout == null ? "" : stdout;
    this.stderr = stderr == null ? "" : stderr;
  }

  /** @return the command line */
  public String command() {
    return command;
  }

  /** @return the exit code, {@code -1} when the command could not be started */
  public int exitCode() {
    return exitCode;
  }

  /** @return the captured standard output */
  public String stdout() {
    return stdout;
  }

  /** @return the captured standard error */
  public String stderr() {
    return stderr;
  }
}
