package com.boostercloud.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Properties;

/** The version of the CLI (the {@code version} of {@code package.json} in TypeScript, {@code config.version}). */
public final class CliVersion {
  /** The CLI version, for example {@code 4.1.0}; new projects depend on this version of the framework. */
  public static final String VERSION = load();

  /** The name shown by {@code boost --version}. */
  public static final String NAME = "booster-cli";

  private CliVersion() {}

  /**
   * The line printed by {@code boost --version}, in oclif's format:
   * {@code booster-cli/4.1.0 darwin-aarch64 java-27.0.1}.
   *
   * @return the version line
   */
  public static String versionLine() {
    return NAME + "/" + VERSION + " " + platform() + "-" + System.getProperty("os.arch") + " java-"
        + System.getProperty("java.version");
  }

  private static String platform() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.startsWith("mac") || os.startsWith("darwin")) {
      return "darwin";
    }
    if (os.startsWith("windows")) {
      return "win32";
    }
    return os.replace(' ', '-');
  }

  private static String load() {
    try (InputStream in = CliVersion.class.getResourceAsStream("booster-cli.properties")) {
      if (in == null) {
        throw new IllegalStateException("The booster-cli.properties resource is missing from the CLI");
      }
      Properties properties = new Properties();
      properties.load(in);
      return properties.getProperty("version").trim();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
