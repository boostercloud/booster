package com.boostercloud.cli.services;

import java.util.Map;
import java.util.Optional;

/**
 * The fully qualified names of the framework's user-facing API that generated code refers to (ARCHITECTURE.md
 * §6b). They follow the module map: decorators live in {@code com.boostercloud.framework.core.decorators} and the
 * concepts of {@code framework-types/src/concepts} in {@code com.boostercloud.framework.types.concepts}.
 *
 * <p>The CLI has no compile-time dependency on the framework, so these names are the single place to update if
 * the framework moves a class.
 */
public final class FrameworkApi {
  /** Package of the framework annotations ({@code @Command}, {@code @Entity}...). */
  public static final String DECORATORS = "com.boostercloud.framework.core.decorators";

  /** Package of the framework concepts ({@code Register}, {@code UUID}...). */
  public static final String CONCEPTS = "com.boostercloud.framework.types.concepts";

  /** {@code Booster} entry point. */
  public static final String BOOSTER = "com.boostercloud.framework.core.Booster";

  /** Interface implemented by configuration classes. */
  public static final String BOOSTER_CONFIGURATOR = "com.boostercloud.framework.core.BoosterConfigurator";

  /** Java package of the local provider ({@code config.providerPackage}). */
  public static final String LOCAL_PROVIDER_PACKAGE = "com.boostercloud.framework.provider.local";

  /** Java package of the Azure provider ({@code config.providerPackage}). */
  public static final String AZURE_PROVIDER_PACKAGE = "com.boostercloud.framework.provider.azure";

  /** Maven group of every framework artifact. */
  public static final String GROUP_ID = "com.boostercloud";

  /** Maven artifact of the framework core, which identifies a Booster project. */
  public static final String CORE_ARTIFACT_ID = "booster-framework-core";

  private static final Map<String, String> KNOWN_TYPES =
      Map.ofEntries(
          Map.entry("Command", DECORATORS + ".Command"),
          Map.entry("Event", DECORATORS + ".Event"),
          Map.entry("Entity", DECORATORS + ".Entity"),
          Map.entry("Reduces", DECORATORS + ".Reduces"),
          Map.entry("ReadModel", DECORATORS + ".ReadModel"),
          Map.entry("Projects", DECORATORS + ".Projects"),
          Map.entry("Query", DECORATORS + ".Query"),
          Map.entry("EventHandler", DECORATORS + ".EventHandler"),
          Map.entry("ScheduledCommand", DECORATORS + ".ScheduledCommand"),
          Map.entry("Register", CONCEPTS + ".Register"),
          Map.entry("UUID", CONCEPTS + ".UUID"),
          Map.entry("QueryInfo", CONCEPTS + ".QueryInfo"),
          Map.entry("ProjectionResult", CONCEPTS + ".ProjectionResult"),
          Map.entry("ReadModelAction", CONCEPTS + ".ReadModelAction"));

  private FrameworkApi() {}

  /**
   * The fully qualified name of a framework type by its simple name.
   *
   * @param simpleName for example {@code Reduces}
   * @return the qualified name, if the type is known
   */
  public static Optional<String> qualifiedName(String simpleName) {
    return Optional.ofNullable(KNOWN_TYPES.get(simpleName));
  }

  /**
   * The fully qualified name of a framework type that must be known.
   *
   * @param simpleName for example {@code Reduces}
   * @return the qualified name
   * @throws IllegalArgumentException when the type is not part of {@link FrameworkApi}
   */
  public static String require(String simpleName) {
    return qualifiedName(simpleName)
        .orElseThrow(() -> new IllegalArgumentException("Unknown framework type: " + simpleName));
  }
}
