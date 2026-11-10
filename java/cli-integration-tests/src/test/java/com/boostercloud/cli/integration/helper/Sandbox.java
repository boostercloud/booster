package com.boostercloud.cli.integration.helper;

import java.nio.file.Path;
import java.util.List;

/**
 * The sandbox projects of the generator, build and clean tests: the Java counterpart of {@code createSandboxProject}
 * and {@code removeSandboxProject} ({@code packages/cli/src/common/sandbox.ts}), which the TypeScript suite imports
 * to copy its test application ({@code src/}, {@code package.json}, {@code tsconfig.json}) into
 * {@code <name>-integration-sandbox}.
 *
 * <p>Here the application copied is {@code src/test/resources/test-app} (a small cart application: a command, an
 * event, an entity, a read model, a role, the configuration and an injectable command), and the Booster version of
 * its {@code pom.xml} is set to the version under test, as {@code overrideWithBoosterLocalDependencies} points the
 * TypeScript sandboxes at the packages of the monorepo.
 */
public final class Sandbox {
  /** The placeholder of the Booster version in {@code test-app/pom.xml}. */
  public static final String BOOSTER_VERSION_PLACEHOLDER = "booster_version_placeholder";

  /** The package of the test application. */
  public static final String BASE_PACKAGE = "com.example.cartdemo";

  private Sandbox() {}

  /**
   * {@code createSandboxProject(sandboxPathFor(name))}: removes any previous sandbox and copies the test application.
   *
   * @param name the sandbox name ({@code build}, {@code command}, ...)
   * @return the sandbox directory
   */
  public static Path createSandboxProject(String name) {
    Path sandbox = FileHelper.sandboxPathFor(name);
    FileHelper.removeFolders(List.of(sandbox));
    FileHelper.copyDirectory(CliTestEnvironment.testAppDirectory(), sandbox);
    Path pom = sandbox.resolve("pom.xml");
    FileHelper.writeFileContent(
        pom,
        FileHelper.readFileContent(pom).replace(BOOSTER_VERSION_PLACEHOLDER, CliTestEnvironment.boosterVersion()));
    return sandbox;
  }

  /**
   * {@code removeFolders([sandbox])} after the tests; kept with {@code -Dbooster.it.keepSandboxes=true}.
   *
   * @param sandbox the sandbox directory (ignored when {@code null})
   */
  public static void removeSandboxProject(Path sandbox) {
    if (sandbox != null && !CliTestEnvironment.keepSandboxes()) {
      FileHelper.removeFolders(List.of(sandbox));
    }
  }

  /**
   * @param sandbox a sandbox created from the test application (or a generated project with the same group)
   * @param packageName the sub-package ({@code commands}, {@code readmodels}...; empty for the base package)
   * @param className the simple class name
   * @return {@code src/main/java/com/example/cartdemo/<packageName>/<className>.java}
   */
  public static Path sourceFile(Path sandbox, String packageName, String className) {
    return sourceFile(sandbox, BASE_PACKAGE, packageName, className);
  }

  /**
   * @param project a Maven project
   * @param basePackage the package of its main class
   * @param packageName the sub-package (empty for the base package)
   * @param className the simple class name
   * @return the source file of the class below {@code src/main/java}
   */
  public static Path sourceFile(Path project, String basePackage, String packageName, String className) {
    String fqn = packageName.isEmpty() ? basePackage : basePackage + "." + packageName;
    return project
        .resolve("src/main/java")
        .resolve(fqn.replace('.', '/'))
        .resolve(className + ".java");
  }

  /**
   * @param sandbox a sandbox created from the test application
   * @param packageName the sub-package (empty for the base package)
   * @param className the simple class name
   * @return {@code target/classes/com/example/cartdemo/<packageName>/<className>.class}
   */
  public static Path classFile(Path sandbox, String packageName, String className) {
    String fqn = packageName.isEmpty() ? BASE_PACKAGE : BASE_PACKAGE + "." + packageName;
    return sandbox.resolve("target/classes").resolve(fqn.replace('.', '/')).resolve(className + ".class");
  }
}
