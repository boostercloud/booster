package com.boostercloud.cli.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates minimal Booster Java projects for the tests. */
public final class TestProjects {
  /** The base package of the test projects. */
  public static final String BASE_PACKAGE = "com.example.mock";

  private TestProjects() {}

  /**
   * A pom that depends on the framework core with the given version.
   *
   * @param coreVersion the version of {@code booster-framework-core}
   * @return the pom text
   */
  public static String pom(String coreVersion) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>mock-project</artifactId>
          <version>0.1.0</version>
          <properties>
            <booster.version>%s</booster.version>
            <booster.mainClass>%s.Main</booster.mainClass>
          </properties>
          <dependencies>
            <dependency>
              <groupId>com.boostercloud</groupId>
              <artifactId>booster-framework-core</artifactId>
              <version>${booster.version}</version>
            </dependency>
          </dependencies>
        </project>
        """
        .formatted(coreVersion, BASE_PACKAGE);
  }

  /**
   * Creates a project with a pom, a main class calling {@code Booster.run} and a configuration class with the
   * {@code local} and {@code production} environments.
   *
   * @param dir the project directory
   * @param coreVersion the framework version
   * @return the project directory
   */
  public static Path create(Path dir, String coreVersion) {
    write(dir.resolve("pom.xml"), pom(coreVersion));
    write(
        source(dir, "Main"),
        """
        package com.example.mock;

        import com.boostercloud.framework.core.Booster;

        public class Main {
          public static void main(String[] args) {
            Booster.run(Main.class, args);
          }
        }
        """);
    write(
        source(dir, "config/Config"),
        """
        package com.example.mock.config;

        import com.boostercloud.framework.core.Booster;
        import com.boostercloud.framework.core.BoosterConfigurator;

        public class Config implements BoosterConfigurator {
          @Override
          public void configure() {
            Booster.configure("local", config -> {
              config.appName = "mock-app";
              config.providerPackage = "com.boostercloud.framework.provider.local";
            });
            Booster.configure("production", config -> {
              config.appName = "mock-app";
              config.providerPackage = "com.boostercloud.framework.provider.azure";
            });
          }
        }
        """);
    return dir;
  }

  /**
   * The source file of a class of the test project.
   *
   * @param dir the project directory
   * @param relativeClass the class path below the base package, e.g. {@code events/PostCreated}
   * @return the file
   */
  public static Path source(Path dir, String relativeClass) {
    return dir.resolve("src/main/java/com/example/mock/" + relativeClass + ".java");
  }

  /**
   * Writes a UTF-8 file, creating the parent directories.
   *
   * @param file the file
   * @param content the content
   */
  public static void write(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a UTF-8 file.
   *
   * @param file the file
   * @return its content
   */
  public static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
