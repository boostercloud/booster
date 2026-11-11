package com.boostercloud.cli.integration.helper;

import com.boostercloud.application.tester.Sleep;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs the real {@code boost} launcher ({@code java/cli/bin/boost}, which runs the CLI jar built by the reactor), as
 * {@code execa.command(`${cliPath} ...`, { cwd })} and {@code child_process.exec} do in the TypeScript suite.
 *
 * <p>Every invocation gets the environment of the test JVM plus:
 *
 * <ul>
 *   <li>{@code JAVA_HOME} = the JDK running the tests (JDK 27), used by the launcher, by Maven and for the
 *       application;</li>
 *   <li>{@code PATH} starting with the {@code bin} of the Maven running the suite;</li>
 *   <li>{@code BOOSTER_ENV=test}, as the {@code integration/cli} npm script sets it;</li>
 *   <li>{@code MAVEN_ARGS=-s <settings.xml>}: the projects build against the isolated repository
 *       ({@link IsolatedMavenRepository});</li>
 *   <li>{@code GIT_CONFIG_GLOBAL}/{@code GIT_CONFIG_NOSYSTEM}: git gets its own identity
 *       ({@link IsolatedMavenRepository#gitConfigFile()});</li>
 *   <li>{@code LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT} = a free port: the local provider opens its websocket server
 *       whenever an application loads it (also for {@code deploy} and injected commands), and the default 65529 may be
 *       taken by another build;</li>
 * </ul>
 *
 * and without the variables that would change the CLI's behaviour or add noise to its standard error
 * ({@code BOOSTER_CLI_HOOK}, {@code BOOSTER_INTERNAL_LOCAL_PORT}, {@code BOOSTER_CLI_JAR}, {@code BOOST_JAVA_OPTS},
 * {@code FORCE_COLOR}, {@code JAVA_TOOL_OPTIONS}, {@code JDK_JAVA_OPTIONS}, {@code _JAVA_OPTIONS}). The standard input
 * is closed at once (the CLI then answers every prompt with its default), unless answers are given.
 *
 * <p>Every invocation is appended to {@link CliTestEnvironment#cliLog()}.
 */
public final class Boost {
  /** How long a CLI invocation may take (the TypeScript CLI suite's mocha timeout). */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

  private static final List<String> REMOVED_VARIABLES =
      List.of(
          "BOOSTER_CLI_HOOK",
          "BOOSTER_INTERNAL_LOCAL_PORT",
          "BOOSTER_CLI_JAR",
          "BOOST_JAVA_OPTS",
          "FORCE_COLOR",
          "JAVA_TOOL_OPTIONS",
          "JDK_JAVA_OPTIONS",
          "_JAVA_OPTIONS",
          "MAVEN_ARGS");

  private Boost() {}

  /**
   * {@code execa.command(`${cliPath} <commandLine>`, { cwd })}: the command line is split on spaces.
   *
   * @param directory the working directory
   * @param commandLine the arguments, separated by spaces
   * @return the result, whatever the exit code
   */
  public static CliResult command(Path directory, String commandLine) {
    List<String> arguments = Arrays.stream(commandLine.split(" ")).filter(s -> !s.isEmpty()).toList();
    return run(directory, arguments);
  }

  /**
   * @param directory the working directory
   * @param arguments the arguments of {@code boost}
   * @return the result, whatever the exit code
   */
  public static CliResult run(Path directory, String... arguments) {
    return run(directory, List.of(arguments));
  }

  /**
   * @param directory the working directory
   * @param arguments the arguments of {@code boost}
   * @return the result, whatever the exit code
   */
  public static CliResult run(Path directory, List<String> arguments) {
    return run(directory, arguments, Map.of());
  }

  /**
   * @param directory the working directory
   * @param arguments the arguments of {@code boost}
   * @param environment variables to add ({@code null} values remove a variable)
   * @return the result, whatever the exit code
   */
  public static CliResult run(Path directory, List<String> arguments, Map<String, String> environment) {
    try (RunningBoost running = spawn(directory, arguments, environment, List.of())) {
      running.awaitExit(DEFAULT_TIMEOUT);
      return running.result();
    }
  }

  /**
   * Runs the CLI and answers its prompts ({@code handlePrompt} of {@code cli.project.integration.ts}): each answer is
   * written once, in order, when the output printed since the previous answer contains its prompt; the standard
   * input is closed after the last one.
   *
   * @param directory the working directory
   * @param arguments the arguments of {@code boost}
   * @param answers the answers
   * @return the result, whatever the exit code
   */
  public static CliResult runWithPrompts(Path directory, List<String> arguments, List<PromptAnswer> answers) {
    try (RunningBoost running = spawn(directory, arguments, Map.of(), answers)) {
      running.awaitExit(DEFAULT_TIMEOUT);
      return running.result();
    }
  }

  /**
   * Starts the CLI without waiting for it ({@code runCommandAsync} of the TypeScript {@code cli-helper.ts}).
   *
   * @param directory the working directory
   * @param arguments the arguments of {@code boost}
   * @param environment variables to add ({@code null} values remove a variable)
   * @return the running CLI
   */
  public static RunningBoost spawn(Path directory, List<String> arguments, Map<String, String> environment) {
    return spawn(directory, arguments, environment, List.of());
  }

  private static RunningBoost spawn(
      Path directory, List<String> arguments, Map<String, String> environment, List<PromptAnswer> answers) {
    Path launcher = CliTestEnvironment.boostLauncher();
    if (!Files.isRegularFile(CliTestEnvironment.cliJar())) {
      throw new IllegalStateException(
          "The CLI jar " + CliTestEnvironment.cliJar() + " does not exist: build the reactor with -am");
    }
    List<String> command = new ArrayList<>();
    command.add(launcher.toString());
    command.addAll(arguments);
    ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
    Map<String, String> env = builder.environment();
    env.clear();
    env.putAll(environment());
    environment.forEach((name, value) -> {
      if (value == null) {
        env.remove(name);
      } else {
        env.put(name, value);
      }
    });
    Process process;
    try {
      process = Processes.track(builder.start());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start " + command, e);
    }
    return new RunningBoost(process, directory, arguments, answers);
  }

  /**
   * The environment of every invocation (see the class description), with a new free websocket port.
   *
   * @return the variables
   */
  public static Map<String, String> environment() {
    Map<String, String> env = new HashMap<>(System.getenv());
    REMOVED_VARIABLES.forEach(env::remove);
    String javaHome = System.getProperty("java.home");
    env.put("JAVA_HOME", javaHome);
    String pathVariable = env.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
    String path = env.getOrDefault(pathVariable, "");
    env.put(
        pathVariable,
        CliTestEnvironment.mavenHome().resolve("bin") + File.pathSeparator + Path.of(javaHome).resolve("bin")
            + File.pathSeparator + path);
    env.put("BOOSTER_ENV", "test");
    env.put("MAVEN_ARGS", "-s " + IsolatedMavenRepository.settingsFile());
    env.put("GIT_CONFIG_GLOBAL", IsolatedMavenRepository.gitConfigFile().toString());
    env.put("GIT_CONFIG_NOSYSTEM", "1");
    env.put("LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT", Integer.toString(Ports.freePort()));
    return env;
  }

  /**
   * Appends an invocation to the CLI log.
   *
   * @param result the finished invocation
   * @param started when it started
   */
  static void log(CliResult result, Instant started) {
    Path log = CliTestEnvironment.cliLog();
    String entry = "\n=== " + started + " (" + Duration.between(started, Instant.now()).toMillis() + " ms) "
        + result.describe() + "\n";
    try {
      Files.createDirectories(log.getParent());
      synchronized (Boost.class) {
        Files.writeString(log, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A CLI process that has been started: its output, its descendants and how to stop it. */
  public static final class RunningBoost implements AutoCloseable {
    private final Process process;
    private final Path directory;
    private final List<String> arguments;
    private final OutputCapture stdout;
    private final OutputCapture stderr;
    private final Instant started = Instant.now();
    private final Map<PromptAnswer, Boolean> pendingAnswers = new LinkedHashMap<>();
    private final StringBuilder sinceLastAnswer = new StringBuilder();
    private CliResult result;

    RunningBoost(Process process, Path directory, List<String> arguments, List<PromptAnswer> answers) {
      this.process = process;
      this.directory = directory;
      this.arguments = List.copyOf(arguments);
      answers.forEach(answer -> pendingAnswers.put(answer, Boolean.FALSE));
      if (answers.isEmpty()) {
        closeInput();
      }
      this.stdout = new OutputCapture(process.getInputStream(), "boost-stdout", answers.isEmpty() ? null : this::onOutput);
      this.stderr = new OutputCapture(process.getErrorStream(), "boost-stderr", null);
    }

    /**
     * @return the underlying process (the CLI JVM: the launcher {@code exec}s Java)
     */
    public Process process() {
      return process;
    }

    /**
     * @return the standard output so far
     */
    public String stdout() {
      return stdout.text();
    }

    /**
     * @return the standard error so far
     */
    public String stderr() {
      return stderr.text();
    }

    /**
     * @return whether the CLI is still running
     */
    public boolean isAlive() {
      return process.isAlive();
    }

    /**
     * Waits until something listens on the port, failing when the CLI exits first or the time is up.
     *
     * @param port the port
     * @param timeout the maximum time
     */
    public void awaitListening(int port, Duration timeout) {
      long deadline = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadline) {
        Processes.rememberDescendants(process);
        if (!process.isAlive()) {
          throw new AssertionError("The CLI exited before listening on port " + port + ":\n" + snapshot());
        }
        if (Ports.isListening(port)) {
          Processes.rememberDescendants(process);
          return;
        }
        Sleep.sleep(200);
      }
      throw new AssertionError("Nothing listens on port " + port + " after " + timeout + ":\n" + snapshot());
    }

    /**
     * @return every descendant seen so far (Maven, the application JVM)
     */
    public List<ProcessHandle> descendants() {
      return Processes.rememberDescendants(process);
    }

    /** Sends SIGTERM to the CLI ({@code process.kill(pid)} of the TypeScript {@code local/stop/setup.ts}). */
    public void terminate() {
      Processes.rememberDescendants(process);
      process.destroy();
    }

    /**
     * Waits for the CLI to exit; kills it with its descendants and fails when the time is up.
     *
     * @param timeout the maximum time
     * @return the exit code
     */
    public int awaitExit(Duration timeout) {
      try {
        boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
          String output = snapshot();
          Processes.killTree(process);
          throw new AssertionError("boost " + String.join(" ", arguments) + " did not finish in " + timeout + ":\n"
              + output);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        Processes.killTree(process);
        throw new IllegalStateException("Interrupted while waiting for the CLI", e);
      }
      stdout.awaitEnd(10_000);
      stderr.awaitEnd(10_000);
      result = new CliResult(directory, arguments, process.exitValue(), stripFinalNewline(stdout.text()),
          stripFinalNewline(stderr.text()));
      log(result, started);
      Processes.release(process);
      return process.exitValue();
    }

    /**
     * @return the result of the finished CLI
     * @throws IllegalStateException when it has not been awaited
     */
    public CliResult result() {
      if (result == null) {
        throw new IllegalStateException("The CLI has not finished yet");
      }
      return result;
    }

    /** Kills the CLI and its descendants if any is still alive. */
    @Override
    public void close() {
      if (process.isAlive() || Processes.rememberDescendants(process).stream().anyMatch(ProcessHandle::isAlive)) {
        String output = snapshot();
        Processes.killTree(process);
        if (result == null) {
          log(new CliResult(directory, arguments, -1, output, ""), started);
        }
      }
    }

    private String snapshot() {
      return "--- stdout\n" + stdout.text() + "\n--- stderr\n" + stderr.text();
    }

    private synchronized void onOutput(String chunk) {
      sinceLastAnswer.append(chunk);
      for (Map.Entry<PromptAnswer, Boolean> entry : pendingAnswers.entrySet()) {
        if (!entry.getValue() && entry.getKey().matches(sinceLastAnswer)) {
          entry.setValue(Boolean.TRUE);
          sinceLastAnswer.setLength(0);
          write(entry.getKey().answer() + PromptAnswer.INTRO);
          break;
        }
      }
      if (!pendingAnswers.containsValue(Boolean.FALSE)) {
        closeInput();
      }
    }

    private void write(String text) {
      try {
        OutputStream in = process.getOutputStream();
        in.write(text.getBytes(StandardCharsets.UTF_8));
        in.flush();
      } catch (IOException e) {
        // the CLI exited or closed its input: the assertions on its output will tell
      }
    }

    private void closeInput() {
      try {
        process.getOutputStream().close();
      } catch (IOException e) {
        // already closed
      }
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
  }
}
