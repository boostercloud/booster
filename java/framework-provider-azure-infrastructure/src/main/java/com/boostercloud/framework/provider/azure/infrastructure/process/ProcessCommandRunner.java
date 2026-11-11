package com.boostercloud.framework.provider.azure.infrastructure.process;

import com.boostercloud.framework.common.RunCommandError;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Runs commands with {@link ProcessBuilder}, like {@code runCommand} of {@code framework-common-helpers}: no shell,
 * the executable looked up in the {@code PATH}, the current environment plus {@link Command#environment()}, output
 * echoed chunk by chunk to {@link System#out} / {@link System#err} unless {@link Command#ignoreLogs()}, and a
 * {@link RunCommandError} carrying the standard error when the exit code is not 0.
 */
public final class ProcessCommandRunner implements CommandRunner {

  /** The shared instance. */
  public static final ProcessCommandRunner INSTANCE = new ProcessCommandRunner();

  private ProcessCommandRunner() {}

  @Override
  public String run(Command command) {
    ProcessBuilder builder = new ProcessBuilder(command.arguments()).directory(command.workingDirectory().toFile());
    builder.environment().putAll(command.environment());
    Process process;
    try {
      process = builder.start();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not run '" + command.commandLine() + "' in " + command.workingDirectory() + ": " + e.getMessage(), e);
    }
    StringBuffer stdout = new StringBuffer();
    StringBuffer stderr = new StringBuffer();
    Thread out = pump(process.getInputStream(), stdout, command.ignoreLogs() ? null : System.out);
    Thread err = pump(process.getErrorStream(), stderr, command.ignoreLogs() ? null : System.err);
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
      throw new IllegalStateException("Interrupted while running '" + command.commandLine() + "'", e);
    }
  }

  private static Thread pump(InputStream stream, StringBuffer accumulator, PrintStream echo) {
    return Thread.ofVirtual().start(() -> {
      byte[] buffer = new byte[8192];
      try (stream) {
        int read;
        while ((read = stream.read(buffer)) != -1) {
          String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
          accumulator.append(chunk);
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
