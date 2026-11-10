package com.boostercloud.cli.integration.helper;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Where everything the suite needs lives. The values come from the system properties the profile {@code e2e} of
 * {@code cli-integration-tests/pom.xml} passes to failsafe, so a test run outside that profile fails at once with an
 * explanation instead of half-working.
 *
 * <p>Replaces the relative paths of the TypeScript suite ({@code path.join('..', '..', 'cli', 'bin', 'run')}, the
 * current working directory as the place for sandboxes and {@code integration/fixtures}) and the
 * {@code require('../../../package.json').version} of {@code cli.project.integration.ts}.
 */
public final class CliTestEnvironment {
  private CliTestEnvironment() {}

  /**
   * @return the version of the CLI and of the framework artifacts ({@code BOOSTER_VERSION} in TypeScript)
   */
  public static String boosterVersion() {
    return property("booster.version");
  }

  /**
   * @return {@code java/cli}, the directory holding {@code bin/boost}
   */
  public static Path cliHome() {
    return path("booster.it.cliHome");
  }

  /**
   * @return the executable CLI jar the reactor built, which {@code bin/boost} finds in {@code ../target}
   */
  public static Path cliJar() {
    return path("booster.it.cliJar");
  }

  /**
   * @return the launcher every test runs ({@code bin/boost}, {@code bin/boost.cmd} on Windows)
   */
  public static Path boostLauncher() {
    return cliHome().resolve("bin").resolve(isWindows() ? "boost.cmd" : "boost");
  }

  /**
   * @return the isolated local repository holding the framework artifacts of this reactor
   */
  public static Path isolatedRepository() {
    return path("booster.it.repository");
  }

  /**
   * @return the local repository of the build running the suite, used read-only as a remote repository
   */
  public static Path userRepository() {
    return path("booster.it.userRepository");
  }

  /**
   * @return the home of the Maven running the suite (its {@code bin} goes first in the {@code PATH} of the CLI)
   */
  public static Path mavenHome() {
    return path("booster.it.mavenHome");
  }

  /**
   * @return the directory for sandboxes, generated settings and logs ({@code target/booster-it})
   */
  public static Path workDirectory() {
    return path("booster.it.work");
  }

  /**
   * @return where the sandbox projects are created ({@code <work>/sandboxes})
   */
  public static Path sandboxesDirectory() {
    return workDirectory().resolve("sandboxes");
  }

  /**
   * @return the file every CLI invocation is logged to
   */
  public static Path cliLog() {
    return workDirectory().resolve("logs").resolve("cli.log");
  }

  /**
   * @return the expected generated files ({@code integration/fixtures} in TypeScript)
   */
  public static Path fixturesDirectory() {
    return resourceDirectory("fixtures");
  }

  /**
   * @return the application every sandbox starts from (the TypeScript test app copied by
   *     {@code createSandboxProject})
   */
  public static Path testAppDirectory() {
    return resourceDirectory("test-app");
  }

  /**
   * @return whether sandboxes are kept after the tests ({@code -Dbooster.it.keepSandboxes=true}, for debugging)
   */
  public static boolean keepSandboxes() {
    return Boolean.getBoolean("booster.it.keepSandboxes");
  }

  /**
   * @return whether the tests run on Windows
   */
  public static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }

  private static Path path(String name) {
    return Path.of(property(name)).toAbsolutePath().normalize();
  }

  private static String property(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "The system property '"
              + name
              + "' is not set. The CLI integration tests run with the Maven profile 'e2e': from java/, run"
              + " mvn -B -Pe2e -pl cli-integration-tests -am verify");
    }
    return value;
  }

  private static Path resourceDirectory(String name) {
    URL url = CliTestEnvironment.class.getClassLoader().getResource(name);
    if (url == null) {
      throw new IllegalStateException("The test resource directory '" + name + "' is not on the class path");
    }
    try {
      Path directory = Path.of(url.toURI());
      if (!Files.isDirectory(directory)) {
        throw new IllegalStateException("The test resource '" + name + "' is not a directory: " + directory);
      }
      return directory;
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Invalid resource URL " + url, e);
    }
  }
}
