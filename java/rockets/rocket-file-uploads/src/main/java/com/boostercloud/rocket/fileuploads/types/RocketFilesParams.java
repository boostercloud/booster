package com.boostercloud.rocket.fileuploads.types;

import java.util.List;
import java.util.Locale;

/**
 * Port of {@code rocket-files-params.ts}: the rocket function id and the selection of a user configuration by storage
 * name. The interfaces and the provider type of that file are {@link RocketFilesUserConfiguration},
 * {@link RocketFilesConfiguration} and {@link RocketProviderPackageType}.
 */
public final class RocketFilesParams {

  /** {@code functionID}: the id of the rocket function the uploads are dispatched to. */
  public static final String FUNCTION_ID = "rocket-files";

  private RocketFilesParams() {}

  /**
   * {@code getUserConfiguration(rocketFilesConfiguration, storageAccountName?)}: without a name (or with an empty one,
   * JavaScript falsiness) the only configuration; otherwise the first one whose storage name matches ignoring case.
   *
   * @param rocketFilesConfiguration the rocket configuration
   * @param storageAccountName the storage name, or {@code null}
   * @return the user configuration
   * @throws IllegalStateException {@code Missing userConfiguration} when there is none
   * @throws IllegalArgumentException {@code Missing storageAccountName} (several configurations and no name) or
   *     {@code Storage account name <name> not found}
   */
  public static RocketFilesUserConfiguration getUserConfiguration(
      RocketFilesConfiguration rocketFilesConfiguration, String storageAccountName) {
    List<RocketFilesUserConfiguration> configurations = rocketFilesConfiguration.userConfiguration();
    if (configurations == null || configurations.isEmpty()) {
      throw new IllegalStateException("Missing userConfiguration");
    }
    boolean missingName = storageAccountName == null || storageAccountName.isEmpty();
    if (missingName && configurations.size() > 1) {
      throw new IllegalArgumentException("Missing storageAccountName");
    }
    if (missingName) {
      return configurations.get(0);
    }
    String wanted = storageAccountName.toLowerCase(Locale.ROOT);
    return configurations.stream()
        .filter(configuration -> configuration.storageName().toLowerCase(Locale.ROOT).equals(wanted))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Storage account name " + storageAccountName + " not found"));
  }
}
