package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.types.EnvironmentVariables;

/**
 * Port of {@code constants.ts}: the Azure storage account name and the port of the local presigned URLs.
 */
public final class Constants {

  /** {@code LOCAL_PORT_ENV}: the variable the local infrastructure sets to the server port when it mounts the rocket. */
  public static final String LOCAL_PORT_ENV = "ROCKET_FILES_LOCAL_PORT";

  /** The port {@link #localPort()} falls back to. */
  public static final String DEFAULT_LOCAL_PORT = "3000";

  private static final int MAX_ACCOUNT_NAME_LENGTH = 24;

  private Constants() {}

  /**
   * {@code azureStorageName(applicationName, environmentName, storageAccountNameParameter?)}: the given name when it
   * is not empty, else {@code <applicationName>rf<environmentName>} without {@code -} and {@code _}, cut to 24
   * characters (not lower-cased, not validated, as in TypeScript; a missing name renders as {@code undefined}).
   *
   * @param applicationName the application name
   * @param environmentName the environment name
   * @param storageAccountNameParameter the configured storage name, or {@code null}
   * @return the storage account name
   */
  public static String azureStorageName(String applicationName, String environmentName,
      String storageAccountNameParameter) {
    String defaultAccountName = (jsString(applicationName) + "rf" + jsString(environmentName)).replaceAll("[-_]", "");
    if (defaultAccountName.length() > MAX_ACCOUNT_NAME_LENGTH) {
      defaultAccountName = defaultAccountName.substring(0, MAX_ACCOUNT_NAME_LENGTH);
    }
    return storageAccountNameParameter == null || storageAccountNameParameter.isEmpty()
        ? defaultAccountName : storageAccountNameParameter;
  }

  /**
   * {@code localPort()}: {@code ROCKET_FILES_LOCAL_PORT} (in-process overrides first, see {@link EnvironmentVariables})
   * or {@code "3000"} when unset or empty.
   *
   * @return the port of the local presigned URLs
   */
  public static String localPort() {
    return EnvironmentVariables.getOrDefault(LOCAL_PORT_ENV, DEFAULT_LOCAL_PORT);
  }

  private static String jsString(String value) {
    return value == null ? "undefined" : value;
  }
}
