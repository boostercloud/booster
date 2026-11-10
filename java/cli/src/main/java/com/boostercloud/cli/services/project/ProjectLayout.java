package com.boostercloud.cli.services.project;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.java.JavaNames;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Where things are in a Booster Java project, derived from its {@code pom.xml}.
 *
 * <ul>
 *   <li>The main class is the {@value #MAIN_CLASS_PROPERTY} property; its package is the base package of every
 *       generated resource ({@code <base>.commands}, {@code <base>.entities}...).
 *   <li>Sources live in {@code <build><sourceDirectory>} ({@code src/main/java} by default).
 *   <li>The application runs from {@code target/classes} plus the runtime dependencies that the generated pom
 *       copies to {@code target/lib}.
 * </ul>
 *
 * @param projectDir the absolute project directory
 * @param pom the project's pom
 * @param mainClass the fully qualified name of the main class
 * @param sourceDirectory the source directory, relative to the project directory
 */
public record ProjectLayout(Path projectDir, PomFile pom, String mainClass, Path sourceDirectory) {
  /** The pom property naming the application's main class. */
  public static final String MAIN_CLASS_PROPERTY = "booster.mainClass";

  /** Maven's default source directory. */
  public static final String DEFAULT_SOURCE_DIRECTORY = "src/main/java";

  /**
   * Reads the layout of a project.
   *
   * @param projectDir the project directory
   * @return the layout
   * @throws CliException when the pom is missing or invalid, or does not define a valid main class
   */
  public static ProjectLayout of(Path projectDir) {
    Path absolute = projectDir.toAbsolutePath().normalize();
    PomFile pom = PomFile.read(absolute.resolve("pom.xml"));
    String mainClass =
        pom.property(MAIN_CLASS_PROPERTY)
            .filter(s -> !s.isBlank())
            .orElseThrow(
                () ->
                    new CliException(
                        "The pom.xml file does not define the '"
                            + MAIN_CLASS_PROPERTY
                            + "' property with the main class of the application"));
    if (!JavaNames.isPackageName(mainClass)) {
      throw new CliException(
          "The '" + MAIN_CLASS_PROPERTY + "' property of the pom.xml file is not a valid class name: " + mainClass);
    }
    Path sourceDirectory = Path.of(pom.sourceDirectory().orElse(DEFAULT_SOURCE_DIRECTORY));
    if (sourceDirectory.isAbsolute()) {
      sourceDirectory = absolute.relativize(sourceDirectory.normalize());
    }
    return new ProjectLayout(absolute, pom, mainClass, sourceDirectory);
  }

  /** @return the package of the main class, the root of every generated resource */
  public String basePackage() {
    return JavaNames.packageOf(mainClass);
  }

  /**
   * The package of a kind of resource.
   *
   * @param subpackage for example {@code commands}
   * @return {@code <base package>.<subpackage>}
   */
  public String packageName(String subpackage) {
    String base = basePackage();
    return base.isEmpty() ? subpackage : base + "." + subpackage;
  }

  /**
   * The directory of a package, relative to the project directory.
   *
   * @param subpackage for example {@code commands}
   * @return for example {@code src/main/java/com/example/commands}
   */
  public Path packageDirectory(String subpackage) {
    return sourceDirectory.resolve(packageName(subpackage).replace('.', File.separatorChar));
  }

  /** @return the absolute source directory */
  public Path absoluteSourceDirectory() {
    return projectDir.resolve(sourceDirectory);
  }

  /** @return the source file of the main class */
  public Path mainSourceFile() {
    return absoluteSourceDirectory().resolve(mainClass.replace('.', File.separatorChar) + ".java");
  }

  /** @return the absolute build directory ({@code target} unless the pom says otherwise) */
  public Path buildDirectory() {
    return projectDir.resolve(pom.buildDirectory().orElse("target"));
  }

  /** @return the directory with the compiled classes */
  public Path classesDirectory() {
    return buildDirectory().resolve("classes");
  }

  /** @return the directory where the generated pom copies the runtime dependencies */
  public Path libDirectory() {
    return buildDirectory().resolve("lib");
  }

  /**
   * The class path used to launch the application: the compiled classes and every jar in the lib directory (a
   * {@code *} wildcard, expanded by the {@code java} launcher).
   *
   * @return the class path entries
   */
  public List<String> runtimeClasspath() {
    return List.of(classesDirectory().toString(), libDirectory().resolve("*").toString());
  }
}
