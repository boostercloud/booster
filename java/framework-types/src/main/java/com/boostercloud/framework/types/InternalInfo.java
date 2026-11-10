package com.boostercloud.framework.types;

/**
 * Port of {@code internal-info.ts}: the port of the local provider's HTTP server.
 */
public final class InternalInfo {

  /** Name of the environment variable holding the local port ({@code BOOSTER_INTERNAL_LOCAL_PORT}). */
  public static final String BOOSTER_LOCAL_PORT = "BOOSTER_INTERNAL_LOCAL_PORT";

  /** Port used when {@link #BOOSTER_LOCAL_PORT} is unset or empty. */
  public static final String DEFAULT_LOCAL_PORT = "3000";

  private InternalInfo() {}

  /**
   * {@code process.env[BOOSTER_LOCAL_PORT] || '3000'}: a string, {@code "3000"} when the variable is unset or empty.
   * Reads {@link EnvironmentVariables}, so a value set with {@link #setBoosterLocalPort(int)} wins.
   *
   * @return the local port
   */
  public static String boosterLocalPort() {
    return EnvironmentVariables.getOrDefault(BOOSTER_LOCAL_PORT, DEFAULT_LOCAL_PORT);
  }

  /**
   * The Java equivalent of the CLI's {@code process.env[BOOSTER_LOCAL_PORT] = port}.
   *
   * @param port the port the local server listens on
   */
  public static void setBoosterLocalPort(int port) {
    EnvironmentVariables.set(BOOSTER_LOCAL_PORT, Integer.toString(port));
  }
}
