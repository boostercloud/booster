package com.boostercloud.framework.types;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access to environment variables, the Java replacement of {@code process.env}.
 *
 * <p>Node programs can assign {@code process.env[NAME] = value} (the CLI does it for
 * {@code BOOSTER_INTERNAL_LOCAL_PORT}); a JVM cannot change its own environment, so this class keeps an in-process
 * override table consulted before {@link System#getenv(String)}. Every Booster module reads environment variables
 * through {@link #get(String)} so overrides (and tests) work uniformly.
 */
public final class EnvironmentVariables {

  /** Name of the variable holding the current environment ({@code BOOSTER_ENV}). */
  public static final String BOOSTER_ENV = "BOOSTER_ENV";

  /** System property alternative to {@link #BOOSTER_ENV}, for tests and embedded use. */
  public static final String BOOSTER_ENV_PROPERTY = "booster.env";

  /** When {@code true}, {@code Booster.start} runs the commands of {@link BoosterConfig#injectable}. */
  public static final String BOOSTER_CLI_HOOK = "BOOSTER_CLI_HOOK";

  private static final Map<String, String> OVERRIDES = new ConcurrentHashMap<>();

  private EnvironmentVariables() {}

  /**
   * @param name variable name
   * @return the override set with {@link #set(String, String)}, else the process environment value
   */
  public static Optional<String> get(String name) {
    Objects.requireNonNull(name, "name");
    String override = OVERRIDES.get(name);
    if (override != null) {
      return Optional.of(override);
    }
    return Optional.ofNullable(System.getenv(name));
  }

  /**
   * JavaScript's {@code process.env[name] || fallback}: an unset <em>or empty</em> variable yields the fallback.
   *
   * @param name variable name
   * @param fallback value used when the variable is unset or empty
   * @return the value or the fallback
   */
  public static String getOrDefault(String name, String fallback) {
    return get(name).filter(value -> !value.isEmpty()).orElse(fallback);
  }

  /**
   * The equivalent of {@code process.env[name] = value} for the current process.
   *
   * @param name variable name
   * @param value new value (not null)
   */
  public static void set(String name, String value) {
    OVERRIDES.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
  }

  /**
   * Removes an override set with {@link #set(String, String)}.
   *
   * @param name variable name
   */
  public static void unset(String name) {
    OVERRIDES.remove(name);
  }

  /** Removes every override (for tests). */
  public static void clearOverrides() {
    OVERRIDES.clear();
  }

  /**
   * The current Booster environment: the {@code booster.env} system property if set, else {@code BOOSTER_ENV}.
   * Blank values count as unset.
   *
   * @return the environment name, if any
   */
  public static Optional<String> boosterEnvironment() {
    String property = System.getProperty(BOOSTER_ENV_PROPERTY);
    if (property != null && !property.isBlank()) {
      return Optional.of(property.trim());
    }
    return get(BOOSTER_ENV).map(String::trim).filter(value -> !value.isEmpty());
  }
}
