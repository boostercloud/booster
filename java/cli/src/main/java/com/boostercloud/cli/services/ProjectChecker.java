package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.generator.target.FileDir;
import com.boostercloud.cli.services.project.PomFile;
import com.boostercloud.cli.services.project.ProjectLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Checks that the CLI runs inside a Booster project, asks before overwriting files, and compares the CLI version
 * with the project's Booster version ({@code services/project-checker.ts}).
 *
 * <p>A Booster Java project has a {@code pom.xml} that depends on {@code com.boostercloud:booster-framework-core}
 * and names its main class in the {@code booster.mainClass} property; the main class must start the application
 * with {@code Booster.run(}. (A TypeScript project is recognised by its {@code tsconfig.json} and an
 * {@code index.ts} calling {@code Booster.start(}.)
 */
public final class ProjectChecker {
  private static final String RECOGNITION_ERROR =
      "There was an error when recognizing the application. Make sure you are in the root path of a Booster project:\n";

  private ProjectChecker() {}

  /**
   * {@link #checkItIsABoosterProject} for the current directory.
   *
   * @param ctx the CLI context
   */
  public static void checkCurrentDirIsABoosterProject(CliContext ctx) {
    checkItIsABoosterProject(ctx.cwd());
  }

  /**
   * Checks that a directory holds a Booster Java project.
   *
   * @param projectPath the directory
   * @throws CliException {@code There was an error when recognizing the application...} followed by the reason
   */
  public static void checkItIsABoosterProject(Path projectPath) {
    Path projectAbsolutePath = projectPath.toAbsolutePath().normalize();
    try {
      ProjectLayout layout = ProjectLayout.of(projectAbsolutePath);
      if (layout.pom().dependency(FrameworkApi.GROUP_ID, FrameworkApi.CORE_ARTIFACT_ID).isEmpty()) {
        throw new CliException(
            "The pom.xml file does not depend on " + FrameworkApi.GROUP_ID + ":" + FrameworkApi.CORE_ARTIFACT_ID);
      }
      checkMainFileIsBooster(layout.mainSourceFile());
    } catch (CliException e) {
      throw new CliException(RECOGNITION_ERROR + e.getMessage(), e);
    }
  }

  static void checkMainFileIsBooster(Path mainFilePath) {
    String contents;
    try {
      contents = Files.readString(mainFilePath, StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      throw new CliException("Cannot find the main application file " + mainFilePath, e);
    } catch (IOException e) {
      throw new CliException("Cannot read the main application file " + mainFilePath + ": " + e, e);
    }
    if (!contents.contains("Booster.run(")) {
      throw new CliException(
          "The main application file does not start a Booster application. Verify you are in the right project");
    }
  }

  /**
   * Before creating a project, asks whether an existing folder with the same name should be overwritten, and
   * deletes it if the user agrees.
   *
   * @param ctx the CLI context
   * @param name the project name
   * @throws CliException when the user refuses
   */
  public static void checkProjectAlreadyExists(CliContext ctx, String name) {
    Path projectDirectoryPath = ProjectInitializer.projectDir(ctx, name);
    if (Files.exists(projectDirectoryPath)) {
      boolean confirm =
          ctx.prompter()
              .confirmPrompt(Brand.dangerize("Project folder \"" + name + "\" already exists. Do you want to overwrite it?"));
      if (!confirm) {
        throw new CliException("The folder you're trying to use already exists. Please use another project name");
      }
      deleteRecursively(projectDirectoryPath);
    }
  }

  /**
   * Before generating a resource, asks whether an existing file should be overwritten, and deletes it if the user
   * agrees. Always logs {@code Checking if resource already exists...}.
   *
   * @param ctx the CLI context
   * @param name the resource name
   * @param placementDir the directory of the resource, relative to the project
   * @param extension the file extension, dot included
   * @throws CliException {@code The '<type>' resource "<file>" already exists. Please use another resource name}
   *     when the user refuses
   */
  public static void checkResourceExists(CliContext ctx, String name, String placementDir, String extension) {
    Path resourcePath = Generator.filePath(ctx, FileDir.of(name, placementDir, extension));
    boolean resourceExists = Files.exists(resourcePath);
    String resourceType = Generator.getResourceType(placementDir);
    ctx.logger().info(Brand.mellancholize("Checking if resource already exists..."));
    if (resourceExists) {
      boolean confirm =
          ctx.prompter()
              .confirmPrompt(
                  Brand.dangerize("Resource: \"" + name + extension + "\" already exists. Do you want to overwrite it?"));
      if (!confirm) {
        throw new CliException(
            "The '"
                + resourceType
                + "' resource \""
                + name
                + extension
                + "\" already exists. Please use another resource name");
      }
      deleteRecursively(resourcePath);
    }
  }

  /**
   * Compares the CLI version with the Booster version of the project in the current directory.
   *
   * @param ctx the CLI context
   * @param version the CLI version
   * @throws CliException when the project cannot be recognised or the versions are incompatible
   */
  public static void checkCurrentDirBoosterVersion(CliContext ctx, String version) {
    checkBoosterVersion(ctx, version, ctx.cwd());
  }

  static void checkBoosterVersion(CliContext ctx, String cliVersion, Path projectPath) {
    String projectVersion = getBoosterVersion(projectPath);
    compareVersionsAndDisplayMessages(ctx.logger(), cliVersion, projectVersion);
  }

  /**
   * The version of {@code com.boostercloud:booster-framework-core} the project depends on. Version ranges keep
   * their lower bound and qualifiers such as {@code -SNAPSHOT} are dropped ({@code 4.1.0-SNAPSHOT} is
   * {@code 4.1.0}).
   *
   * @param projectPath the project directory
   * @return the version
   * @throws CliException when the project or the dependency cannot be read
   */
  static String getBoosterVersion(Path projectPath) {
    Path projectAbsolutePath = projectPath.toAbsolutePath().normalize();
    try {
      PomFile pom = PomFile.read(projectAbsolutePath.resolve("pom.xml"));
      PomFile.Dependency core =
          pom.dependency(FrameworkApi.GROUP_ID, FrameworkApi.CORE_ARTIFACT_ID)
              .orElseThrow(
                  () ->
                      new CliException(
                          "The pom.xml file does not depend on "
                              + FrameworkApi.GROUP_ID
                              + ":"
                              + FrameworkApi.CORE_ARTIFACT_ID));
      if (core.version() == null || core.version().contains("${")) {
        throw new CliException(
            "Cannot determine the version of " + FrameworkApi.CORE_ARTIFACT_ID + " declared in the pom.xml file");
      }
      return normalizeVersion(core.version());
    } catch (CliException e) {
      throw new CliException(RECOGNITION_ERROR + e.getMessage(), e);
    }
  }

  static String normalizeVersion(String version) {
    String v = version.strip();
    if (v.startsWith("[") || v.startsWith("(")) {
      v = v.substring(1);
      int comma = v.indexOf(',');
      if (comma >= 0) {
        v = v.substring(0, comma);
      }
      v = v.replace("]", "").replace(")", "").strip();
    }
    int dash = v.indexOf('-');
    return dash > 0 ? v.substring(0, dash) : v;
  }

  static void compareVersionsAndDisplayMessages(Logger logger, String cliVersion, String projectVersion) {
    Semver cliSemVersion = new Semver(cliVersion);
    Semver projectSemVersion = new Semver(projectVersion);
    if (cliSemVersion.equals(projectSemVersion)) {
      return;
    }
    if (cliSemVersion.equalsInBreakingSection(projectSemVersion)) {
      if (cliSemVersion.equalsInFeatureSection(projectSemVersion)) {
        if (!cliSemVersion.equalsInFixSection(projectSemVersion)) {
          logger.info(
              "WARNING: Project Booster version differs in the 'fix' section. CLI version: "
                  + cliVersion
                  + ". Project Booster version: "
                  + projectVersion);
        }
      } else if (cliSemVersion.greaterInFeatureSectionThan(projectSemVersion)) {
        throw new HigherCliVersionError(cliVersion, projectVersion, "feature");
      } else {
        throw new LowerCliVersionError(cliVersion, projectVersion, "feature");
      }
    } else if (cliSemVersion.greaterInBreakingSectionThan(projectSemVersion)) {
      throw new HigherCliVersionError(cliVersion, projectVersion, "breaking");
    } else {
      throw new LowerCliVersionError(cliVersion, projectVersion, "breaking");
    }
  }

  static void deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(path)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    } catch (IOException e) {
      throw new CliException("Could not delete " + path + ": " + e, e);
    }
  }

  /** The CLI is newer than the project's Booster dependencies. */
  public static final class HigherCliVersionError extends CliException {
    private static final long serialVersionUID = 1L;

    private final String cliVersion;
    private final String projectVersion;
    private final String section;

    HigherCliVersionError(String cliVersion, String projectVersion, String section) {
      super(
          "CLI version "
              + cliVersion
              + " is higher than your project Booster version "
              + projectVersion
              + " in the '"
              + section
              + "' section. Please upgrade your project Booster dependencies.");
      this.cliVersion = cliVersion;
      this.projectVersion = projectVersion;
      this.section = section;
    }

    /** @return the CLI version */
    public String cliVersion() {
      return cliVersion;
    }

    /** @return the project's Booster version */
    public String projectVersion() {
      return projectVersion;
    }

    /** @return {@code breaking} or {@code feature} */
    public String section() {
      return section;
    }
  }

  /** The CLI is older than the project's Booster dependencies. */
  public static final class LowerCliVersionError extends CliException {
    private static final long serialVersionUID = 1L;

    private final String cliVersion;
    private final String projectVersion;
    private final String section;

    LowerCliVersionError(String cliVersion, String projectVersion, String section) {
      super(
          "CLI version "
              + cliVersion
              + " is lower than your project Booster version "
              + projectVersion
              + ". Please upgrade your Booster CLI (com.boostercloud:booster-cli) to the same version: "
              + projectVersion);
      this.cliVersion = cliVersion;
      this.projectVersion = projectVersion;
      this.section = section;
    }

    /** @return the CLI version */
    public String cliVersion() {
      return cliVersion;
    }

    /** @return the project's Booster version */
    public String projectVersion() {
      return projectVersion;
    }

    /** @return {@code breaking} or {@code feature} */
    public String section() {
      return section;
    }
  }
}
