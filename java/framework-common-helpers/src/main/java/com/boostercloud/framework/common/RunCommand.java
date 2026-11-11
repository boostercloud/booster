package com.boostercloud.framework.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Port of {@code run-command.ts}: runs a command in a directory.
 *
 * <p>Like TypeScript the command line is split on single spaces (no shell, no quoting: {@code a  b} gives
 * {@code ["a", "", "b"]}) and the executable is looked up in the {@code PATH}. Unless {@code ignoreLogs}, every chunk of
 * output is echoed to {@link System#out} / {@link System#err} as it arrives.
 */
public final class RunCommand {
  private RunCommand() {}

  /**
   * @param path the working directory
   * @param command the command line
   * @return the running process
   */
  public static Process runCommandAsync(String path, String command) {
    return runCommandAsync(path, command, false);
  }

  /**
   * {@code runCommandAsync(path, command, ignoreLogs = false)}: starts the command and returns the live process (the
   * caller owns it, e.g. a long-running server). When logs are echoed this method consumes the process output; pass
   * {@code ignoreLogs = true} to read it yourself.
   *
   * @param path the working directory
   * @param command the command line
   * @param ignoreLogs whether not to echo the output
   * @return the running process
   * @throws UncheckedIOException when the process cannot be started
   */
  public static Process runCommandAsync(String path, String command, boolean ignoreLogs) {
    Process process = start(path, command);
    if (!ignoreLogs) {
      pump(process.getInputStream(), null, System.out);
      pump(process.getErrorStream(), null, System.err);
    }
    return process;
  }

  /**
   * @param path the working directory
   * @param command the command line
   * @return the standard output
   */
  public static String runCommand(String path, String command) {
    return runCommand(path, command, false);
  }

  /**
   * {@code runCommand(path, command, ignoreLogs = false)}: runs the command to completion.
   *
   * @param path the working directory
   * @param command the command line
   * @param ignoreLogs whether not to echo the output
   * @return the full standard output when the exit code is 0
   * @throws RunCommandError when the exit code is not 0 (its message is the standard error)
   * @throws UncheckedIOException when the process cannot be started
   */
  public static String runCommand(String path, String command, boolean ignoreLogs) {
    Process process = start(path, command);
    StringBuffer stdout = new StringBuffer();
    StringBuffer stderr = new StringBuffer();
    Thread out = pump(process.getInputStream(), stdout, ignoreLogs ? null : System.out);
    Thread err = pump(process.getErrorStream(), stderr, ignoreLogs ? null : System.err);
    try {
      int exitCode = process.waitFor();
      out.join();
      err.join();
      if (exitCode != 0) {
        throw new RunCommandError(exitCode, stdout.toString(), stderr.toString());
      }
      return stdout.toString();
    } catch (InterruptedException e) {
      process.destroy();
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while running '" + command + "'", e);
    }
  }

  /**
   * @param command a command line
   * @return the arguments, split like JavaScript's {@code command.split(' ')}
   */
  public static List<String> splitCommand(String command) {
    return List.of(command.split(" ", -1));
  }

  private static Process start(String path, String command) {
    try {
      return new ProcessBuilder(splitCommand(command)).directory(new File(path)).start();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not run '" + command + "' in " + path + ": " + e.getMessage(), e);
    }
  }

  private static Thread pump(InputStream stream, StringBuffer accumulator, PrintStream echo) {
    return Thread.ofVirtual().start(() -> {
      byte[] buffer = new byte[8192];
      try (stream) {
        int read;
        while ((read = stream.read(buffer)) != -1) {
          String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
          if (accumulator != null) {
            accumulator.append(chunk);
          }
          if (echo != null) {
            echo.println(chunk);
          }
        }
      } catch (IOException ignored) {
        // The process ended or its stream was closed
      }
    });
  }
}
