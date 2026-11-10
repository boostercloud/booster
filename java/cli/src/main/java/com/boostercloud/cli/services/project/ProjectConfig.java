package com.boostercloud.cli.services.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the CLI knows about a compiled project and the selected environment: the Java counterpart of the
 * {@code BoosterConfig} that the TypeScript CLI obtains by loading the project in-process.
 *
 * @param projectPath the project directory
 * @param environmentName the selected environment ({@code BOOSTER_ENV})
 * @param layout the project layout (main class, class path...)
 * @param environments the environments found in the sources
 */
public record ProjectConfig(
    Path projectPath, String environmentName, ProjectLayout layout, ConfiguredEnvironments environments) {

  /**
   * The {@code appName} statically assigned in the selected environment.
   *
   * @return the name, if the sources assign a literal
   */
  public Optional<String> configuredAppName() {
    return Optional.ofNullable(environments.appNames().get(environmentName));
  }

  /**
   * The application name: the literal {@code config.appName} of the selected environment or, when it cannot be
   * determined statically, the Maven artifact id (which is what {@code new:project} assigns).
   *
   * @return the application name
   */
  public String appName() {
    return configuredAppName().orElse(layout.pom().artifactId());
  }

  /** @return the fully qualified name of the main class */
  public String mainClass() {
    return layout.mainClass();
  }

  /** @return the class path used to launch the application */
  public List<String> classpath() {
    return layout.runtimeClasspath();
  }
}
