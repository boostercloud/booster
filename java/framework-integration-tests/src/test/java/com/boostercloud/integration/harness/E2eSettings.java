package com.boostercloud.integration.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Where the end-to-end harness finds what the build produced, and the knobs it reads from system properties. The
 * {@code e2e} Maven profile passes {@link #MODULE_DIRECTORY_PROPERTY} and {@link #MAIN_CLASS_PROPERTY} to failsafe;
 * without them (an IDE run from the module directory) the working directory and {@link #DEFAULT_MAIN_CLASS} are used.
 *
 * <p>The launched application uses the class path of a project built by the {@code boost} CLI:
 * {@code target/classes} plus every jar of {@code target/lib} (copied by {@code maven-dependency-plugin} during
 * {@code package}).
 */
public final class E2eSettings {

  /** System property: the {@code framework-integration-tests} module directory. */
  public static final String MODULE_DIRECTORY_PROPERTY = "booster.e2e.moduleDirectory";
  /** System property: the application's main class. */
  public static final String MAIN_CLASS_PROPERTY = "booster.e2e.mainClass";
  /** System property: {@code true} keeps the sandbox project directories after the run (for debugging). */
  public static final String KEEP_PROJECTS_PROPERTY = "booster.e2e.keepProjects";
  /** System property: seconds an application may take to become ready (default 120). */
  public static final String READINESS_TIMEOUT_PROPERTY = "booster.e2e.readinessTimeoutSeconds";
  /** System property: extra JVM options of the launched applications, separated by spaces. */
  public static final String APP_JVM_OPTIONS_PROPERTY = "booster.e2e.appJvmOptions";
  /** The main class of the integration application. */
  public static final String DEFAULT_MAIN_CLASS = "com.boostercloud.integration.Main";
  /** Default readiness timeout. */
  public static final Duration DEFAULT_READINESS_TIMEOUT = Duration.ofSeconds(120);

  private E2eSettings() {}

  /**
   * @return the module directory (absolute)
   */
  public static Path moduleDirectory() {
    String configured = System.getProperty(MODULE_DIRECTORY_PROPERTY);
    Path directory = configured == null || configured.isBlank()
        ? Path.of(System.getProperty("user.dir"))
        : Path.of(configured);
    return directory.toAbsolutePath().normalize();
  }

  /**
   * @return {@code <module>/target}
   */
  public static Path targetDirectory() {
    return moduleDirectory().resolve("target");
  }

  /**
   * @return {@code <module>/target/classes}, the compiled application
   */
  public static Path classesDirectory() {
    return targetDirectory().resolve("classes");
  }

  /**
   * @return {@code <module>/target/lib}, the application's runtime dependencies
   */
  public static Path libDirectory() {
    return targetDirectory().resolve("lib");
  }

  /**
   * @return {@code <module>/target/e2e-logs}, one {@code <name>.log} per launched application
   */
  public static Path logsDirectory() {
    return targetDirectory().resolve("e2e-logs");
  }

  /**
   * @return {@code <module>/target/e2e-projects}, the sandbox project directories
   */
  public static Path projectsDirectory() {
    return targetDirectory().resolve("e2e-projects");
  }

  /**
   * @return the application's main class
   */
  public static String mainClass() {
    String configured = System.getProperty(MAIN_CLASS_PROPERTY);
    return configured == null || configured.isBlank() ? DEFAULT_MAIN_CLASS : configured;
  }

  /**
   * @return whether sandbox projects are kept after the run
   */
  public static boolean keepProjects() {
    return Boolean.getBoolean(KEEP_PROJECTS_PROPERTY);
  }

  /**
   * @return how long an application may take to become ready
   */
  public static Duration readinessTimeout() {
    String configured = System.getProperty(READINESS_TIMEOUT_PROPERTY);
    return configured == null || configured.isBlank()
        ? DEFAULT_READINESS_TIMEOUT
        : Duration.ofSeconds(Long.parseLong(configured.trim()));
  }

  /**
   * @return extra JVM options of the launched applications
   */
  public static java.util.List<String> appJvmOptions() {
    String configured = System.getProperty(APP_JVM_OPTIONS_PROPERTY);
    if (configured == null || configured.isBlank()) {
      return java.util.List.of();
    }
    return java.util.List.of(configured.trim().split("\\s+"));
  }

  /**
   * The JVM that runs the applications, as the CLI chooses it: {@code $JAVA_HOME/bin/java} when {@code JAVA_HOME} is
   * set, otherwise the JVM running the tests.
   *
   * @return the {@code java} executable
   */
  public static Path javaExecutable() {
    Path home = Optional.ofNullable(System.getenv("JAVA_HOME"))
        .filter(value -> !value.isBlank())
        .map(Path::of)
        .filter(path -> Files.isExecutable(path.resolve("bin").resolve(executableName())))
        .orElse(Path.of(System.getProperty("java.home")));
    return home.resolve("bin").resolve(executableName());
  }

  private static String executableName() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
  }
}
