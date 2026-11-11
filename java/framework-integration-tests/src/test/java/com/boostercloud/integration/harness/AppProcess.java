package com.boostercloud.integration.harness;

import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.integration.helper.FileHelper;
import com.boostercloud.integration.helper.Sandbox;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The integration application running as a separate JVM on the local provider, launched exactly as the {@code boost}
 * CLI launches a project ({@code java/cli/README.md}, "Contract with the framework"):
 *
 * <pre>{@code java -cp <module>/target/classes:<module>/target/lib/* com.boostercloud.integration.Main start --port <N>}</pre>
 *
 * with the test JVM's environment plus {@code BOOSTER_ENV=local}, {@code BOOSTER_INTERNAL_LOCAL_PORT=<N>},
 * {@code LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT=<M>} and {@code BOOSTER_APP_SUFFIX} (when known). Both ports are free
 * ports chosen by the harness (never the fixed TypeScript ones). The working directory is the sandbox project
 * directory, so the local provider keeps its data in {@code <project>/.booster}; it is recreated on the first start
 * (TypeScript's {@code createSandboxProject}) and kept across restarts, so the data survives them.
 *
 * <p>Standard output and error go to {@code target/e2e-logs/<name>.log} (truncated on the first start, appended to on
 * restarts). {@link #start()} returns once the GraphQL endpoint answers and the websocket port accepts connections, and
 * stores the PID in {@code <project>/local_provider.pid} like TypeScript's {@code local/start/setup.ts}. {@link #stop()}
 * sends SIGTERM, waits and checks that the JVM ended through its graceful shutdown (exit code 143, or 0);
 * {@link #kill()} sends SIGKILL (a crash). Every process still running when the test JVM exits is killed.
 *
 * <pre>{@code
 * try (AppProcess app = AppProcess.builder("restart").build().start()) {
 *   ApplicationUnderTest applicationUnderTest = ApplicationUnderTest.of(app);
 *   ...
 *   app.restart();      // graceful stop + start, same ports and data
 *   app.kill();         // crash
 *   app.start();        // recovery
 * }
 * }</pre>
 */
public final class AppProcess implements AutoCloseable {

  /** Exit code of a JVM that ended its shutdown after SIGTERM. */
  public static final int SIGTERM_EXIT_CODE = 143;
  /** Exit code of a JVM killed with SIGKILL. */
  public static final int SIGKILL_EXIT_CODE = 137;
  /** The environment the applications run in. */
  public static final String LOCAL_ENVIRONMENT = "local";
  /** Environment variable with the HTTP port (set by the CLI too). */
  public static final String HTTP_PORT_VARIABLE = "BOOSTER_INTERNAL_LOCAL_PORT";
  /** Environment variable with the port of the local provider's websocket server. */
  public static final String WEBSOCKET_PORT_VARIABLE = "LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT";
  /** Assets copied to the sandbox, as {@code local/start/setup.ts} does. */
  public static final List<String> DEFAULT_ASSETS = List.of("assets", "assetFile.txt");
  /** How long {@link #stop()} waits for the graceful shutdown. */
  public static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(30);

  private static final Set<Process> RUNNING = ConcurrentHashMap.newKeySet();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> RUNNING.forEach(Process::destroyForcibly),
        "e2e-app-process-reaper"));
  }

  private final String name;
  private final Path projectDirectory;
  private final Path logFile;
  private final int httpPort;
  private final int webSocketPort;
  private final String environment;
  private final Map<String, String> extraEnvironment;
  private final List<String> jvmOptions;
  private final List<String> assets;
  private final Duration readinessTimeout;
  private final Duration stopTimeout;
  private final boolean keepProjectDirectory;

  private Process process;
  private Integer lastExitCode;
  private int starts;

  private AppProcess(Builder builder) {
    this.name = builder.name;
    this.projectDirectory = builder.projectDirectory != null
        ? builder.projectDirectory.toAbsolutePath().normalize()
        : E2eSettings.projectsDirectory().resolve(FileHelper.sandboxPathFor(builder.name));
    this.logFile = E2eSettings.logsDirectory().resolve(builder.name + ".log");
    if (builder.httpPort != null) {
      this.httpPort = builder.httpPort;
      this.webSocketPort = builder.webSocketPort;
    } else {
      List<Integer> ports = FreePorts.allocate(2);
      this.httpPort = ports.get(0);
      this.webSocketPort = ports.get(1);
    }
    this.environment = builder.environment;
    this.extraEnvironment = Map.copyOf(builder.extraEnvironment);
    this.jvmOptions = List.copyOf(builder.jvmOptions);
    this.assets = List.copyOf(builder.assets);
    this.readinessTimeout = builder.readinessTimeout;
    this.stopTimeout = builder.stopTimeout;
    this.keepProjectDirectory = builder.keepProjectDirectory;
  }

  /**
   * @param name the name of the application process: its log file is {@code target/e2e-logs/<name>.log} and its
   *     sandbox {@code target/e2e-projects/<name>-integration-sandbox}
   * @return a builder with the defaults
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Builds and starts an application with the defaults.
   *
   * @param name see {@link #builder(String)}
   * @return the running application
   */
  public static AppProcess start(String name) {
    return builder(name).build().start();
  }

  /**
   * Starts the application (again, after {@link #stop()} or {@link #kill()}) and waits until it is ready. The first
   * start recreates the sandbox project directory; later starts keep it.
   *
   * @return this
   * @throws IllegalStateException when it is already running, exits early or is not ready in time (it is killed)
   */
  public synchronized AppProcess start() {
    if (isAlive()) {
      throw new IllegalStateException("The application " + name + " is already running (pid " + pid() + ")");
    }
    try {
      if (starts == 0) {
        Sandbox.createSandboxProject(projectDirectory, assets);
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "", StandardCharsets.UTF_8);
      }
      starts++;
      Files.writeString(logFile, "===== [e2e] start #" + starts + " of " + name + " at " + Instant.now() + ": "
          + String.join(" ", command()) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
      ProcessBuilder processBuilder = new ProcessBuilder(command())
          .directory(projectDirectory.toFile())
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
      processBuilder.environment().putAll(environment());
      lastExitCode = null;
      process = processBuilder.start();
      RUNNING.add(process);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start the application " + name, e);
    }
    FileHelper.storePIDFor(projectDirectory, process.pid());
    awaitReadiness();
    return this;
  }

  /**
   * Stops the application gracefully: SIGTERM, then waits for the JVM to end (its shutdown hook stops the local
   * server and closes the data files).
   *
   * @return the exit code ({@value #SIGTERM_EXIT_CODE}, or 0); the last one when it was not running
   * @throws IllegalStateException when it does not end in time (it is then killed) or ends with another exit code
   */
  public synchronized int stop() {
    if (process == null) {
      throw new IllegalStateException("The application " + name + " was never started");
    }
    if (!process.isAlive()) {
      return exitCodeOf(process);
    }
    process.destroy();
    int exitCode = awaitExit(stopTimeout);
    if (exitCode != SIGTERM_EXIT_CODE && exitCode != 0) {
      throw new IllegalStateException("The application " + name + " ended with exit code " + exitCode
          + " after SIGTERM instead of " + SIGTERM_EXIT_CODE + "; see " + logFile + tail());
    }
    return exitCode;
  }

  /**
   * Kills the application (SIGKILL): a crash, nothing is flushed or closed by the application.
   *
   * @return the exit code ({@value #SIGKILL_EXIT_CODE})
   */
  public synchronized int kill() {
    if (process == null) {
      throw new IllegalStateException("The application " + name + " was never started");
    }
    process.destroyForcibly();
    return awaitExit(stopTimeout);
  }

  /**
   * {@link #stop()} then {@link #start()}: same ports, same project directory, same data.
   *
   * @return this
   */
  public synchronized AppProcess restart() {
    stop();
    return start();
  }

  /**
   * Waits for the process to end by itself (for example after a signal sent through its PID).
   *
   * @param timeout how long to wait
   * @return the exit code
   * @throws IllegalStateException when it does not end in time (it is then killed)
   */
  public synchronized int awaitExit(Duration timeout) {
    if (process == null) {
      throw new IllegalStateException("The application " + name + " was never started");
    }
    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
        RUNNING.remove(process);
        throw new IllegalStateException("The application " + name + " did not stop within " + timeout
            + "; it was killed; see " + logFile + tail());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the application " + name, e);
    }
    return exitCodeOf(process);
  }

  /**
   * Stops the application when it runs (killing it when the graceful stop fails) and deletes the sandbox project
   * directory, unless it is kept ({@link Builder#keepProjectDirectory}, {@code -Dbooster.e2e.keepProjects=true}).
   */
  @Override
  public synchronized void close() {
    try {
      if (isAlive()) {
        try {
          stop();
        } catch (RuntimeException e) {
          System.err.println("[e2e] " + e.getMessage());
          if (isAlive()) {
            kill();
          }
        }
      }
    } finally {
      if (!keepProjectDirectory) {
        FileHelper.removeFolders(List.of(projectDirectory));
      }
    }
  }

  /**
   * @return the launch command: {@code <java> [options] -cp <classes>:<lib>/* <main class> start --port <N>}
   */
  public List<String> command() {
    List<String> command = new ArrayList<>();
    command.add(E2eSettings.javaExecutable().toString());
    command.addAll(jvmOptions);
    command.add("-cp");
    command.add(classpath());
    command.add(E2eSettings.mainClass());
    command.add("start");
    command.add("--port");
    command.add(Integer.toString(httpPort));
    return command;
  }

  /**
   * @return the class path of a CLI-built project: {@code target/classes} and {@code target/lib/*}
   */
  public static String classpath() {
    return E2eSettings.classesDirectory() + File.pathSeparator + E2eSettings.libDirectory().resolve("*");
  }

  /**
   * @return the variables added to the test JVM's environment for the application
   */
  public Map<String, String> environment() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put(EnvironmentVariables.BOOSTER_ENV, environment);
    variables.put(HTTP_PORT_VARIABLE, Integer.toString(httpPort));
    variables.put(WEBSOCKET_PORT_VARIABLE, Integer.toString(webSocketPort));
    EnvironmentVariables.get("BOOSTER_APP_SUFFIX").ifPresent(suffix -> variables.put("BOOSTER_APP_SUFFIX", suffix));
    variables.putAll(extraEnvironment);
    return variables;
  }

  /**
   * @return the name of the application process
   */
  public String name() {
    return name;
  }

  /**
   * @return the sandbox project directory (the application's working directory)
   */
  public Path projectDirectory() {
    return projectDirectory;
  }

  /**
   * @return {@code <project>/.booster}, the local provider's data
   */
  public Path boosterDirectory() {
    return projectDirectory.resolve(".booster");
  }

  /**
   * @return {@code target/e2e-logs/<name>.log}
   */
  public Path logFile() {
    return logFile;
  }

  /**
   * @return the HTTP port
   */
  public int httpPort() {
    return httpPort;
  }

  /**
   * @return the port of the local provider's websocket server
   */
  public int webSocketPort() {
    return webSocketPort;
  }

  /**
   * @return {@code http://localhost:<port>/graphql}
   */
  public String graphqlURL() {
    return "http://localhost:" + httpPort + "/graphql";
  }

  /**
   * @return {@code ws://localhost:<port>/websocket}
   */
  public String websocketURL() {
    return "ws://localhost:" + webSocketPort + "/websocket";
  }

  /**
   * @return {@code http://localhost:<port>/sensor/health/}
   */
  public String healthURL() {
    return "http://localhost:" + httpPort + "/sensor/health/";
  }

  /**
   * @return whether the process is running
   */
  public synchronized boolean isAlive() {
    return process != null && process.isAlive();
  }

  /**
   * @return the PID of the current (or last) process
   * @throws IllegalStateException before the first start
   */
  public synchronized long pid() {
    if (process == null) {
      throw new IllegalStateException("The application " + name + " was never started");
    }
    return process.pid();
  }

  /**
   * @return the exit code of the last process, or {@code null} while it runs or before the first start
   */
  public synchronized Integer exitCode() {
    return process == null || process.isAlive() ? null : exitCodeOf(process);
  }

  /**
   * @return how many times it was started
   */
  public synchronized int starts() {
    return starts;
  }

  /**
   * @return the output written so far
   */
  public String output() {
    try {
      return Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public String toString() {
    return "AppProcess{" + name + ", http " + httpPort + ", websocket " + webSocketPort + ", " + projectDirectory + "}";
  }

  private int exitCodeOf(Process ended) {
    RUNNING.remove(ended);
    if (lastExitCode == null) {
      lastExitCode = ended.exitValue();
    }
    return lastExitCode;
  }

  private void awaitReadiness() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    HttpRequest probe = HttpRequest.newBuilder(URI.create(graphqlURL()))
        .timeout(Duration.ofSeconds(5))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"query { __typename }\"}"))
        .build();
    long deadline = System.nanoTime() + readinessTimeout.toNanos();
    while (true) {
      if (!process.isAlive()) {
        int exitCode = exitCodeOf(process);
        throw new IllegalStateException("The application " + name + " exited with code " + exitCode
            + " before it was ready; see " + logFile + tail());
      }
      if (answers(client, probe) && accepts(webSocketPort)) {
        return;
      }
      if (System.nanoTime() > deadline) {
        process.destroyForcibly();
        awaitExit(Duration.ofSeconds(10));
        throw new IllegalStateException("The application " + name + " was not ready within " + readinessTimeout
            + "; it was killed; see " + logFile + tail());
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for the application " + name, e);
      }
    }
  }

  private static boolean answers(HttpClient client, HttpRequest probe) {
    try {
      return client.send(probe, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static boolean accepts(int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", port), 1000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private String tail() {
    List<String> lines = output().lines().toList();
    List<String> last = lines.subList(Math.max(0, lines.size() - 40), lines.size());
    return last.isEmpty() ? "" : System.lineSeparator() + String.join(System.lineSeparator(), last);
  }

  /** Options of an {@link AppProcess}. */
  public static final class Builder {

    private final String name;
    private Path projectDirectory;
    private Integer httpPort;
    private Integer webSocketPort;
    private String environment = LOCAL_ENVIRONMENT;
    private final Map<String, String> extraEnvironment = new LinkedHashMap<>();
    private final List<String> jvmOptions = new ArrayList<>(E2eSettings.appJvmOptions());
    private List<String> assets = DEFAULT_ASSETS;
    private Duration readinessTimeout = E2eSettings.readinessTimeout();
    private Duration stopTimeout = DEFAULT_STOP_TIMEOUT;
    private boolean keepProjectDirectory = E2eSettings.keepProjects();

    private Builder(String name) {
      Objects.requireNonNull(name, "name");
      if (!name.matches("[A-Za-z0-9._-]+")) {
        throw new IllegalArgumentException("Invalid application process name '" + name + "'");
      }
      this.name = name;
    }

    /**
     * @param directory the sandbox project directory (default {@code target/e2e-projects/<name>-integration-sandbox})
     * @return this
     */
    public Builder projectDirectory(Path directory) {
      this.projectDirectory = directory;
      return this;
    }

    /**
     * @param http the HTTP port (default: a free port)
     * @param webSocket the websocket port (default: a free port)
     * @return this
     */
    public Builder ports(int http, int webSocket) {
      this.httpPort = http;
      this.webSocketPort = webSocket;
      return this;
    }

    /**
     * @param environmentName the Booster environment (default {@code local})
     * @return this
     */
    public Builder environment(String environmentName) {
      this.environment = Objects.requireNonNull(environmentName, "environmentName");
      return this;
    }

    /**
     * @param variable an extra environment variable of the application
     * @param value its value
     * @return this
     */
    public Builder env(String variable, String value) {
      extraEnvironment.put(variable, value);
      return this;
    }

    /**
     * @param option an extra JVM option of the application (e.g. {@code -Xmx256m})
     * @return this
     */
    public Builder jvmOption(String option) {
      jvmOptions.add(option);
      return this;
    }

    /**
     * @param moduleRelativeAssets what to copy into the sandbox (default {@link #DEFAULT_ASSETS})
     * @return this
     */
    public Builder assets(List<String> moduleRelativeAssets) {
      this.assets = List.copyOf(moduleRelativeAssets);
      return this;
    }

    /**
     * @param timeout how long {@link AppProcess#start()} waits for the application
     * @return this
     */
    public Builder readinessTimeout(Duration timeout) {
      this.readinessTimeout = Objects.requireNonNull(timeout, "timeout");
      return this;
    }

    /**
     * @param timeout how long {@link AppProcess#stop()} waits for the graceful shutdown
     * @return this
     */
    public Builder stopTimeout(Duration timeout) {
      this.stopTimeout = Objects.requireNonNull(timeout, "timeout");
      return this;
    }

    /**
     * @param keep whether {@link AppProcess#close()} keeps the sandbox project directory
     * @return this
     */
    public Builder keepProjectDirectory(boolean keep) {
      this.keepProjectDirectory = keep;
      return this;
    }

    /**
     * Allocates the ports; nothing is started yet.
     *
     * @return the application process
     */
    public AppProcess build() {
      return new AppProcess(this);
    }
  }
}
