package com.boostercloud.cli;

import com.boostercloud.cli.common.ColorSupport;
import com.boostercloud.cli.services.Logger;
import com.boostercloud.cli.services.Loggers;
import com.boostercloud.cli.services.OraLogger;
import com.boostercloud.cli.services.PortProbe;
import com.boostercloud.cli.services.Prompter;
import com.boostercloud.cli.services.filesystem.FileSystemService;
import com.boostercloud.cli.services.filesystem.LiveFileSystem;
import com.boostercloud.cli.services.packagemanager.LivePackageManager;
import com.boostercloud.cli.services.packagemanager.PackageManagerService;
import com.boostercloud.cli.services.process.LiveProcess;
import com.boostercloud.cli.services.process.ProcessService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Everything a command needs from its surroundings. It replaces the globals of the TypeScript CLI
 * ({@code process.cwd()}, {@code process.env}, the {@code oraLogger} singleton, inquirer, execa and fs), so that
 * commands can be tested with a temporary directory, a scripted standard input and a fake process runner.
 *
 * <p>{@link #environment()} is mutable: selecting an environment with {@code -e} writes {@code BOOSTER_ENV} there,
 * and every process started by the CLI receives it.
 */
public final class CliContext {
  private final Path cwd;
  private final Map<String, String> environment;
  private final PrintStream out;
  private final PrintStream err;
  private final BufferedReader in;
  private final ProcessService process;
  private final FileSystemService fileSystem;
  private final Clock clock;
  private final String cliVersion;
  private final OraLogger oraLogger;
  private final Logger logger;
  private final Prompter prompter;
  private final PortProbe portProbe;
  private final boolean windows;
  private final Duration pollInterval;
  private final String javaHome;

  private CliContext(Builder b) {
    this.cwd = b.cwd.toAbsolutePath().normalize();
    this.environment = b.environment;
    this.out = b.out;
    this.err = b.err;
    this.in = b.in;
    this.fileSystem = b.fileSystem;
    this.clock = b.clock;
    this.cliVersion = b.cliVersion;
    this.windows = b.windows;
    this.pollInterval = b.pollInterval;
    this.javaHome = b.javaHome;
    this.portProbe = b.portProbe;
    this.process = b.processFactory.apply(this);
    this.oraLogger = new OraLogger(out, b.interactive, b.unicode);
    this.logger = Loggers.logger(oraLogger);
    this.prompter = new Prompter(in, out, oraLogger);
  }

  /**
   * The context of a real invocation: current directory, process environment, standard streams.
   *
   * @return the live context
   */
  public static CliContext live() {
    boolean tty = ColorSupport.isTerminal();
    Charset stdinCharset = charsetProperty("stdin.encoding");
    return builder()
        .cwd(Path.of("").toAbsolutePath())
        .environment(System.getenv())
        .out(System.out)
        .err(System.err)
        .in(new BufferedReader(new InputStreamReader(System.in, stdinCharset)))
        .interactive(tty)
        .unicode(unicodeSupported(System.getenv()))
        .build();
  }

  /**
   * A builder with sensible defaults for tests: no interactive terminal, an empty environment, the live process
   * and file-system services.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** @return the directory where the CLI runs ({@code process.cwd()}) */
  public Path cwd() {
    return cwd;
  }

  /** @return the mutable environment passed to child processes ({@code process.env}) */
  public Map<String, String> environment() {
    return environment;
  }

  /** @return the standard output */
  public PrintStream out() {
    return out;
  }

  /** @return the standard error */
  public PrintStream err() {
    return err;
  }

  /** @return the standard input */
  public BufferedReader in() {
    return in;
  }

  /** @return the process runner */
  public ProcessService process() {
    return process;
  }

  /** @return the file-system service */
  public FileSystemService fileSystem() {
    return fileSystem;
  }

  /** @return the clock used for {@code errors.log} timestamps */
  public Clock clock() {
    return clock;
  }

  /** @return the CLI version ({@code config.version} in oclif) */
  public String cliVersion() {
    return cliVersion;
  }

  /** @return the spinner logger ({@code oraLogger}) */
  public OraLogger oraLogger() {
    return oraLogger;
  }

  /** @return the logger adapter ({@code logger}) */
  public Logger logger() {
    return logger;
  }

  /** @return the interactive prompter */
  public Prompter prompter() {
    return prompter;
  }

  /** @return the probe used to detect when the local server is listening */
  public PortProbe portProbe() {
    return portProbe;
  }

  /** @return whether the CLI runs on Windows */
  public boolean windows() {
    return windows;
  }

  /** @return how often a starting application is polled */
  public Duration pollInterval() {
    return pollInterval;
  }

  /**
   * The package manager inferred from the current directory ({@code LivePackageManager}).
   *
   * @return a new package manager
   */
  public PackageManagerService packageManager() {
    return LivePackageManager.infer(process, fileSystem, windows);
  }

  /**
   * The {@code java} executable used to launch the application: the one of {@code JAVA_HOME} when it is set (the
   * JDK Maven compiles with), otherwise the one running the CLI.
   *
   * @return the path of the executable
   */
  public String javaExecutable() {
    String executable = windows ? "java.exe" : "java";
    String home = environment.get("JAVA_HOME");
    if (home != null && !home.isBlank()) {
      Path candidate = Path.of(home, "bin", executable);
      if (Files.isRegularFile(candidate)) {
        return candidate.toString();
      }
    }
    return Path.of(javaHome, "bin", executable).toString();
  }

  private static Charset charsetProperty(String name) {
    String value = System.getProperty(name);
    try {
      return value == null ? Charset.defaultCharset() : Charset.forName(value);
    } catch (RuntimeException e) {
      return Charset.defaultCharset();
    }
  }

  static boolean unicodeSupported(Map<String, String> env) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.startsWith("windows")) {
      return !"linux".equals(env.get("TERM"));
    }
    return env.containsKey("WT_SESSION")
        || env.containsKey("CI")
        || "vscode".equals(env.get("TERM_PROGRAM"))
        || "xterm-256color".equals(env.get("TERM"));
  }

  /** Builds a {@link CliContext}. */
  public static final class Builder {
    private Path cwd = Path.of("").toAbsolutePath();
    private Map<String, String> environment = new LinkedHashMap<>();
    private PrintStream out = System.out;
    private PrintStream err = System.err;
    private BufferedReader in = new BufferedReader(new java.io.StringReader(""));
    private boolean interactive;
    private boolean unicode = true;
    private FileSystemService fileSystem = new LiveFileSystem();
    private Function<CliContext, ProcessService> processFactory =
        ctx -> new LiveProcess(ctx::environment, ctx.cwd());
    private Clock clock = Clock.systemUTC();
    private String cliVersion = CliVersion.VERSION;
    private PortProbe portProbe = PortProbe.live();
    private boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private Duration pollInterval = Duration.ofMillis(250);
    private String javaHome = System.getProperty("java.home");

    private Builder() {}

    /** @param value the current directory @return this builder */
    public Builder cwd(Path value) {
      this.cwd = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the initial environment (copied) @return this builder */
    public Builder environment(Map<String, String> value) {
      this.environment = new LinkedHashMap<>(value);
      return this;
    }

    /** @param value the standard output @return this builder */
    public Builder out(PrintStream value) {
      this.out = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the standard error @return this builder */
    public Builder err(PrintStream value) {
      this.err = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the standard input @return this builder */
    public Builder in(BufferedReader value) {
      this.in = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the text read as standard input @return this builder */
    public Builder stdin(String value) {
      this.in = new BufferedReader(new java.io.StringReader(value));
      return this;
    }

    /** @param value whether standard output is an interactive terminal @return this builder */
    public Builder interactive(boolean value) {
      this.interactive = value;
      return this;
    }

    /** @param value whether unicode symbols can be printed @return this builder */
    public Builder unicode(boolean value) {
      this.unicode = value;
      return this;
    }

    /** @param value the file-system service @return this builder */
    public Builder fileSystem(FileSystemService value) {
      this.fileSystem = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the process runner @return this builder */
    public Builder process(ProcessService value) {
      Objects.requireNonNull(value);
      this.processFactory = ctx -> value;
      return this;
    }

    /** @param value the clock @return this builder */
    public Builder clock(Clock value) {
      this.clock = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the CLI version @return this builder */
    public Builder cliVersion(String value) {
      this.cliVersion = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the port probe @return this builder */
    public Builder portProbe(PortProbe value) {
      this.portProbe = Objects.requireNonNull(value);
      return this;
    }

    /** @param value whether to behave as on Windows @return this builder */
    public Builder windows(boolean value) {
      this.windows = value;
      return this;
    }

    /** @param value the polling interval of a starting application @return this builder */
    public Builder pollInterval(Duration value) {
      this.pollInterval = Objects.requireNonNull(value);
      return this;
    }

    /** @param value the {@code java.home} used when {@code JAVA_HOME} is not set @return this builder */
    public Builder javaHome(String value) {
      this.javaHome = Objects.requireNonNull(value);
      return this;
    }

    /** @return the context */
    public CliContext build() {
      return new CliContext(this);
    }
  }
}
