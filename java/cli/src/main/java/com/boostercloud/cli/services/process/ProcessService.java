package com.boostercloud.cli.services.process;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Runs external processes ({@code services/process/index.ts}).
 *
 * <p>Every process receives the CLI's environment (including the {@code BOOSTER_ENV} selected with {@code -e}).
 * Besides the buffered {@link #exec} of TypeScript, the Java CLI needs {@link #spawn} to run the user's application
 * attached to the terminal.
 */
public interface ProcessService {
  /**
   * Runs a command line to completion, splitting it on spaces (like {@code execa.command}).
   *
   * @param command the command line
   * @param cwd the working directory
   * @return the formatted output (see {@link #exec(List, Path)})
   * @throws ProcessError when the command cannot be started or exits with a non-zero code
   */
  default String exec(String command, Path cwd) {
    return exec(Arrays.stream(command.trim().split(" +")).filter(s -> !s.isEmpty()).toList(), cwd);
  }

  /**
   * Runs a command to completion with buffered output.
   *
   * @param command the program and its arguments
   * @param cwd the working directory
   * @return {@code "\n" + (stderr empty ? "" : "There were some issues running the command: " + stderr + "\n") +
   *     "\n" + stdout + "\n"}, as the TypeScript implementation does
   * @throws ProcessError wrapping a {@link CommandFailedException} when the command cannot be started or exits
   *     with a non-zero code
   */
  String exec(List<String> command, Path cwd);

  /**
   * The working directory of the CLI.
   *
   * @return the current directory
   */
  Path cwd();

  /**
   * Starts a process that shares the CLI's standard input, output and error. While it runs, stopping the CLI (for
   * example with Ctrl-C) terminates it gracefully.
   *
   * @param command the program and its arguments
   * @param cwd the working directory
   * @return the running process
   * @throws ProcessError when the process cannot be started
   */
  RunningProcess spawn(List<String> command, Path cwd);
}
