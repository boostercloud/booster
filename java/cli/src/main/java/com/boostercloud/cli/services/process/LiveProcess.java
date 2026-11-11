package com.boostercloud.cli.services.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link ProcessService} backed by {@link ProcessBuilder} ({@code services/process/live.impl.ts}, which uses
 * execa).
 */
public final class LiveProcess implements ProcessService {
  /** Seconds a spawned process is given to shut down after the CLI is interrupted, before it is killed. */
  static final long SHUTDOWN_GRACE_SECONDS = 10;

  private final Supplier<Map<String, String>> environment;
  private final Path cwd;

  /**
   * Creates the service.
   *
   * @param environment the environment passed to every process (read at every call, so later changes such as
   *     {@code BOOSTER_ENV} are seen)
   * @param cwd the working directory of the CLI
   */
  public LiveProcess(Supplier<Map<String, String>> environment, Path cwd) {
    this.environment = environment;
    this.cwd = cwd;
  }

  @Override
  public String exec(List<String> command, Path workingDirectory) {
    String commandLine = String.join(" ", command);
    Process process;
    try {
      process = builder(command, workingDirectory).start();
    } catch (IOException e) {
      throw new ProcessError(
          new CommandFailedException(
              "Command failed with ENOENT: " + commandLine + "\n" + e.getMessage(), commandLine, -1, "", "", e));
    }
    try (InputStream stdoutStream = process.getInputStream();
        InputStream stderrStream = process.getErrorStream()) {
      process.getOutputStream().close();
      var stdoutCollector = new Collector(stdoutStream);
      var stderrCollector = new Collector(stderrStream);
      Thread out = Thread.ofVirtual().start(stdoutCollector);
      Thread err = Thread.ofVirtual().start(stderrCollector);
      int exitCode = process.waitFor();
      out.join();
      err.join();
      String stdout = stripFinalNewline(stdoutCollector.text());
      String stderr = stripFinalNewline(stderrCollector.text());
      if (exitCode != 0) {
        StringBuilder message = new StringBuilder("Command failed with exit code " + exitCode + ": " + commandLine);
        if (!stderr.isEmpty()) {
          message.append('\n').append(stderr);
        }
        if (!stdout.isEmpty()) {
          message.append('\n').append(stdout);
        }
        throw new ProcessError(
            new CommandFailedException(message.toString(), commandLine, exitCode, stdout, stderr, null));
      }
      return "\n"
          + (stderr.isEmpty() ? "" : "There were some issues running the command: " + stderr + "\n")
          + "\n"
          + stdout
          + "\n";
    } catch (InterruptedException e) {
      process.destroy();
      Thread.currentThread().interrupt();
      throw new ProcessError(
          new CommandFailedException("Command was interrupted: " + commandLine, commandLine, -1, "", "", e));
    } catch (IOException e) {
      throw new ProcessError(
          new CommandFailedException(
              "Command failed: " + commandLine + "\n" + e.getMessage(), commandLine, -1, "", "", e));
    }
  }

  @Override
  public Path cwd() {
    return cwd;
  }

  @Override
  public RunningProcess spawn(List<String> command, Path workingDirectory) {
    String commandLine = String.join(" ", command);
    Process process;
    try {
      process = builder(command, workingDirectory).inheritIO().start();
    } catch (IOException e) {
      throw new ProcessError(
          new CommandFailedException(
              "Command failed with ENOENT: " + commandLine + "\n" + e.getMessage(), commandLine, -1, "", "", e));
    }
    return new LiveRunningProcess(process);
  }

  private ProcessBuilder builder(List<String> command, Path workingDirectory) {
    ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
    Map<String, String> env = builder.environment();
    env.clear();
    env.putAll(environment.get());
    return builder;
  }

  private static String stripFinalNewline(String text) {
    if (text.endsWith("\r\n")) {
      return text.substring(0, text.length() - 2);
    }
    if (text.endsWith("\n")) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

  private static final class Collector implements Runnable {
    private final InputStream stream;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    Collector(InputStream stream) {
      this.stream = stream;
    }

    @Override
    public void run() {
      try {
        stream.transferTo(buffer);
      } catch (IOException ignored) {
        // the process closed its stream: keep what was read
      }
    }

    String text() {
      return buffer.toString(nativeCharset());
    }
  }

  private static Charset nativeCharset() {
    String encoding = System.getProperty("native.encoding");
    try {
      return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    } catch (RuntimeException e) {
      return StandardCharsets.UTF_8;
    }
  }

  /**
   * A spawned process. While it runs, a shutdown hook forwards the termination of the CLI (Ctrl-C, SIGTERM) to it
   * and waits for it to finish, killing it if it does not stop in time.
   */
  private static final class LiveRunningProcess implements RunningProcess {
    private final Process process;
    private final Thread shutdownHook;

    LiveRunningProcess(Process process) {
      this.process = process;
      this.shutdownHook = new Thread(this::terminate, "boost-child-shutdown");
      try {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
      } catch (IllegalStateException alreadyShuttingDown) {
        terminate();
      }
      process.onExit().thenRun(this::removeHook);
    }

    @Override
    public int waitFor() {
      try {
        return process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        terminate();
        return 130;
      }
    }

    @Override
    public boolean isAlive() {
      return process.isAlive();
    }

    @Override
    public void destroy() {
      process.destroy();
    }

    private void terminate() {
      if (!process.isAlive()) {
        return;
      }
      process.destroy();
      try {
        if (!process.waitFor(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
      }
    }

    private void removeHook() {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException shuttingDown) {
        // the hook is running or about to run; nothing to do
      }
    }
  }
}
