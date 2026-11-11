package com.boostercloud.cli.integration.helper;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of a finished CLI invocation: what {@code execa.command(...)} resolves with in the TypeScript suite.
 * As with execa, one final newline is stripped from each stream.
 *
 * @param directory the working directory
 * @param arguments the arguments given to {@code boost}
 * @param exitCode the exit code
 * @param stdout the standard output
 * @param stderr the standard error
 */
public record CliResult(Path directory, List<String> arguments, int exitCode, String stdout, String stderr) {

  /**
   * @return a description of the invocation for assertion messages
   */
  public String describe() {
    return "boost " + String.join(" ", arguments) + " (in " + directory + ") exited with " + exitCode
        + "\n--- stdout\n" + stdout + "\n--- stderr\n" + stderr;
  }

  @Override
  public String toString() {
    return describe();
  }
}
