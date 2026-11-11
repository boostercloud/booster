package com.boostercloud.cli.services;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.CliVersion;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Errors;
import com.boostercloud.cli.services.java.JavaNames;
import com.boostercloud.cli.services.packagemanager.PackageManagerError;
import com.boostercloud.cli.services.packagemanager.PackageManagerService;
import com.boostercloud.cli.services.project.ResourcePackages;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scaffolds a new Booster Java project ({@code services/project-initializer.ts}): directories, configuration files,
 * dependency resolution and the git repository.
 */
public final class ProjectInitializer {
  /**
   * A file of the new project.
   *
   * @param relativePath the path relative to the project directory ({@code {package}} is replaced by the base
   *     package path)
   * @param template the bundled template, relative to the {@code project} templates folder
   */
  public record ProjectFile(String relativePath, String template) {}

  /** The files of a new project ({@code filesToGenerate}). */
  public static final List<ProjectFile> FILES_TO_GENERATE =
      List.of(
          new ProjectFile("pom.xml", "pom.xml.mustache"),
          new ProjectFile(".gitignore", "gitignore.mustache"),
          new ProjectFile("README.md", "README.md.mustache"),
          new ProjectFile("src/main/java/{package}/Main.java", "Main.java.mustache"),
          new ProjectFile("src/main/java/{package}/config/Config.java", "Config.java.mustache"));

  private ProjectInitializer() {}

  /**
   * Renders and writes the configuration files of the project (pom, {@code .gitignore}, README, main class and
   * configuration class).
   *
   * @param ctx the CLI context
   * @param config the project description
   * @throws CliException when a file cannot be written
   */
  public static void generateConfigFiles(CliContext ctx, ProjectInitializerConfig config) {
    Map<String, Object> view = templateView(config);
    String packagePath = config.basePackage().replace('.', '/');
    for (ProjectFile file : FILES_TO_GENERATE) {
      String rendered = Templates.render(Templates.bundled("project/" + file.template()), view);
      Path renderPath =
          projectDir(ctx, config).resolve(file.relativePath().replace("{package}", packagePath).replace('/', File.separatorChar));
      try {
        Files.createDirectories(renderPath.getParent());
        Files.writeString(renderPath, rendered, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new CliException("Could not write " + renderPath + ": " + e, e);
      }
    }
  }

  /**
   * Resolves all the dependencies of the new project with the package manager inferred from the directory where
   * the CLI runs.
   *
   * @param ctx the CLI context
   * @param config the project description
   * @throws CliException {@code [<name>] Could not install dependencies:} followed by the Maven error
   */
  public static void installDependencies(CliContext ctx, ProjectInitializerConfig config) {
    try {
      PackageManagerService packageManager = ctx.packageManager();
      packageManager.setProjectRoot(projectDir(ctx, config));
      packageManager.installAllDependencies();
    } catch (PackageManagerError e) {
      throw Errors.guardError("Could not install dependencies").apply(e.error());
    }
  }

  /**
   * Creates the project directory with one package per kind of resource under the base package.
   *
   * @param ctx the CLI context
   * @param config the project description
   * @throws CliException when a directory cannot be created
   */
  public static void generateRootDirectory(CliContext ctx, ProjectInitializerConfig config) {
    Path packageDir =
        projectDir(ctx, config).resolve("src/main/java".replace('/', File.separatorChar)).resolve(config.basePackage().replace('.', File.separatorChar));
    try {
      for (String subpackage : ResourcePackages.ALL) {
        Files.createDirectories(packageDir.resolve(subpackage));
      }
      Files.createDirectories(projectDir(ctx, config).resolve("src").resolve("main").resolve("resources"));
    } catch (IOException e) {
      throw new CliException("Could not create the project directories: " + e, e);
    }
  }

  /**
   * Initializes a git repository with an initial commit ({@code git init}, {@code git add -A},
   * {@code git commit -m "Initial commit"}).
   *
   * @param ctx the CLI context
   * @param config the project description
   * @throws CliException {@code Could not initialize git repository} followed by the git output
   */
  public static void initializeGit(CliContext ctx, ProjectInitializerConfig config) {
    Path dir = projectDir(ctx, config);
    try {
      ctx.process().exec(List.of("git", "init"), dir);
      ctx.process().exec(List.of("git", "add", "-A"), dir);
      ctx.process().exec(List.of("git", "commit", "-m", "Initial commit"), dir);
    } catch (CliException e) {
      throw Errors.wrapExecError(e, "Could not initialize git repository");
    }
  }

  /**
   * @param ctx the CLI context
   * @param config the project description
   * @return {@code <cwd>/<project name>}
   */
  public static Path projectDir(CliContext ctx, ProjectInitializerConfig config) {
    return projectDir(ctx, config.projectName());
  }

  /**
   * @param ctx the CLI context
   * @param projectName the project name
   * @return {@code <cwd>/<project name>}
   */
  public static Path projectDir(CliContext ctx, String projectName) {
    return ctx.cwd().resolve(projectName);
  }

  /**
   * The view rendered with the project templates. Besides every field of the configuration it contains the base
   * package, the framework version (the CLI's own version, as in TypeScript) and flags describing the provider.
   *
   * @param config the project description
   * @return the view
   */
  public static Map<String, Object> templateView(ProjectInitializerConfig config) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("projectName", config.projectName());
    view.put("description", config.description());
    view.put("version", config.version());
    view.put("author", config.author());
    view.put("homepage", config.homepage());
    view.put("license", config.license());
    view.put("repository", config.repository());
    view.put("providerPackageName", config.providerPackageName());
    view.put("groupId", config.groupId());
    view.put("basePackage", config.basePackage());
    view.put("basePackagePath", config.basePackage().replace('.', '/'));
    view.put("boosterVersion", CliVersion.VERSION);
    view.put("appNameLiteral", JavaNames.escapeStringLiteral(config.projectName()));
    view.put("productionProvider", config.hasProductionEnvironment());
    view.put("productionProviderLiteral", JavaNames.escapeStringLiteral(config.providerPackageName()));
    view.put("azure", config.usesAzure());
    view.put("customProvider", config.hasProductionEnvironment() && !config.usesAzure());
    return view;
  }
}
