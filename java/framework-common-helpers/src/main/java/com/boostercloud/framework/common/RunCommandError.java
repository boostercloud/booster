package com.boostercloud.framework.common;

/**
 * A command run by {@link RunCommand#runCommand} exited with a non-zero code. TypeScript rejects with the stderr
 * string; the message here is that string.
 */
public class RunCommandError extends RuntimeException {

  private final int exitCode;
  private final String stdout;
  private final String stderr;

  /**
   * @param exitCode the process exit code
   * @param stdout everything the process wrote to stdout
   * @param stderr everything the process wrote to stderr
   */
  public RunCommandError(int exitCode, String stdout, String stderr) {
    super(stderr);
    this.exitCode = exitCode;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  /**
   * @return the exit code
   */
  public int exitCode() {
    return exitCode;
  }

  /**
   * @return the standard output
   */
  public String stdout() {
    return stdout;
  }

  /**
   * @return the standard error
   */
  public String stderr() {
    return stderr;
  }
}
