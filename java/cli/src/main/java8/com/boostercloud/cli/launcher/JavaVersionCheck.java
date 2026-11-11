package com.boostercloud.cli.launcher;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The {@code Main-Class} of the executable jar: checks that the JVM is recent enough for the CLI, then runs
 * {@code com.boostercloud.cli.Main}.
 *
 * <p>This class lives in its own source root ({@code src/main/java8}) and is compiled with {@code --release 8}, so
 * that an older JVM (a {@code JAVA_HOME} or a {@code java} on the {@code PATH} that is not Java 27) can load it and say
 * what is wrong, instead of failing with a localized {@code UnsupportedClassVersionError} about class file versions.
 * It must only use the Java 8 API. The required release is not configured anywhere: it is read from the class file
 * version of {@code Main}, so it always matches the release the CLI was compiled for.
 */
public final class JavaVersionCheck {
  /** The class that runs the CLI. */
  static final String MAIN_CLASS = "com.boostercloud.cli.Main";

  /** Release = class file major version - 44 (Java 8 is 52, Java 27 is 71). */
  private static final int CLASS_FILE_VERSION_OFFSET = 44;

  private JavaVersionCheck() {}

  /**
   * Prints an error and exits with 1 on a JVM older than the CLI's release; otherwise runs the CLI.
   *
   * @param args the command line
   * @throws Throwable whatever the CLI throws
   */
  public static void main(String[] args) throws Throwable {
    String problem =
        problem(System.getProperty("java.specification.version"), System.getProperty("java.home"), requiredRelease());
    if (problem != null) {
      System.err.println(problem);
      System.exit(1);
    }
    MethodHandle main =
        MethodHandles.publicLookup()
            .findStatic(Class.forName(MAIN_CLASS), "main", MethodType.methodType(void.class, String[].class));
    main.invokeExact(args);
  }

  /**
   * @param specificationVersion the {@code java.specification.version} of the running JVM
   * @param javaHome its {@code java.home}
   * @param requiredRelease the oldest Java release the CLI runs on
   * @return the message to print, or {@code null} when the JVM is recent enough (or its version is unknown)
   */
  static String problem(String specificationVersion, String javaHome, int requiredRelease) {
    if (featureVersion(specificationVersion) >= requiredRelease) {
      return null;
    }
    return "boost: Java " + requiredRelease + " or newer is required, but found Java " + specificationVersion
        + " at " + javaHome + ". Set JAVA_HOME to a JDK " + requiredRelease + " (or newer) installation.";
  }

  /**
   * @param specificationVersion {@code 1.8} (Java 8), {@code 9}, {@code 21}, {@code 27}...
   * @return the feature release, or {@link Integer#MAX_VALUE} when it cannot be read (the CLI is then started)
   */
  static int featureVersion(String specificationVersion) {
    if (specificationVersion == null) {
      return Integer.MAX_VALUE;
    }
    String version = specificationVersion.startsWith("1.") ? specificationVersion.substring(2) : specificationVersion;
    int dot = version.indexOf('.');
    if (dot >= 0) {
      version = version.substring(0, dot);
    }
    try {
      return Integer.parseInt(version);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  /**
   * @return the release {@code Main} was compiled for (its class file major version minus 44), or 0 when it cannot
   *     be read (no check)
   */
  static int requiredRelease() {
    InputStream in = JavaVersionCheck.class.getResourceAsStream("/" + MAIN_CLASS.replace('.', '/') + ".class");
    if (in == null) {
      return 0;
    }
    try (DataInputStream data = new DataInputStream(in)) {
      if (data.readInt() != 0xCAFEBABE) {
        return 0;
      }
      data.readUnsignedShort(); // minor version
      return data.readUnsignedShort() - CLASS_FILE_VERSION_OFFSET;
    } catch (IOException e) {
      return 0;
    }
  }
}
