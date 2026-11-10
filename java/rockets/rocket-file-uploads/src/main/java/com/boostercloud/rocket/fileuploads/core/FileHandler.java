package com.boostercloud.rocket.fileuploads.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.RocketFilesConfiguration;
import com.boostercloud.rocket.fileuploads.types.RocketFilesParams;
import com.boostercloud.rocket.fileuploads.types.RocketFilesProviderLibrary;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.Utils;
import java.util.List;

/**
 * The file API applications use from their commands ({@code FileHandler} of {@code file-handler.ts}):
 *
 * <pre>{@code
 * @Command(authorize = All.class)
 * public record FileUploadPut(String directory, String fileName, Optional<String> storageName) {
 *   public static String handle(FileUploadPut command, Register register) {
 *     return new FileHandler(Booster.config(), command.storageName().orElse(null))
 *         .presignedPut(command.directory(), command.fileName());
 *   }
 * }
 * }</pre>
 *
 * <p>The constructor finds the rocket descriptor in {@code config.rockets} (the first one of the Azure or local
 * infrastructure packages), loads its provider runtime library and selects the storage
 * ({@link RocketFilesParams#getUserConfiguration}). Every method first checks the directory against the storage's
 * directory patterns ({@code Invalid directory <directory>}); file names are not validated, as in TypeScript.
 */
public final class FileHandler {

  /** The descriptor packages the handler looks for, in TypeScript's order ({@code infraPackages}). */
  public static final List<String> INFRA_PACKAGES = List.of(
      BoosterRocketFiles.AZURE_INFRASTRUCTURE_PACKAGE, BoosterRocketFiles.LOCAL_INFRASTRUCTURE_PACKAGE);

  private final BoosterConfig config;
  private final RocketDescriptor rocket;
  private final RocketFilesConfiguration rocketFilesConfiguration;
  private final RocketFilesProviderLibrary provider;
  private final RocketFilesUserConfiguration userConfiguration;

  /**
   * {@code new FileHandler(config)}: the only storage of the rocket.
   *
   * @param config the application configuration
   */
  public FileHandler(BoosterConfig config) {
    this(config, null);
  }

  /**
   * {@code new FileHandler(config, storageName)}.
   *
   * @param config the application configuration
   * @param storageName the storage (case-insensitive), or {@code null}/empty for the only one
   * @throws IllegalStateException {@code Rocket not found. Please make sure you have setup the rocket packageName
   *     correctly} without descriptor, {@code Missing userConfiguration} without storages
   * @throws IllegalArgumentException {@code Missing storageAccountName} (several storages and no name),
   *     {@code Storage account name <name> not found}
   */
  public FileHandler(BoosterConfig config, String storageName) {
    this.config = config;
    this.rocket = getRocketFromConfiguration(config);
    this.rocketFilesConfiguration = RocketFilesConfiguration.from(rocket.parameters());
    this.provider = RocketFilesProviders.load(rocketFilesConfiguration.rocketProviderPackage());
    this.userConfiguration = RocketFilesParams.getUserConfiguration(rocketFilesConfiguration, storageName);
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * @return the selected storage
   */
  public RocketFilesUserConfiguration userConfiguration() {
    return userConfiguration;
  }

  /**
   * {@code presignedGet(directory, fileName)}.
   *
   * @param directory the directory
   * @param fileName the file name
   * @return a URL to download the file from
   * @throws IllegalArgumentException {@code Invalid directory <directory>}
   */
  public String presignedGet(String directory, String fileName) {
    checkDirectory(directory);
    return provider.presignedGet(config, userConfiguration, directory, fileName);
  }

  /**
   * {@code presignedPut(directory, fileName)} ({@code Promise<string> | Promise<any>}): a URL string with the Azure
   * and local libraries, typed by the caller ({@code String url = fileHandler.presignedPut(...)}).
   *
   * @param directory the directory
   * @param fileName the file name
   * @param <T> the expected type
   * @return what the client needs to upload the file
   * @throws IllegalArgumentException {@code Invalid directory <directory>}
   */
  @SuppressWarnings("unchecked")
  public <T> T presignedPut(String directory, String fileName) {
    checkDirectory(directory);
    return (T) provider.presignedPut(config, userConfiguration, directory, fileName);
  }

  /**
   * {@code list(directory)}.
   *
   * @param directory the directory
   * @return the files (local: the entries of the directory; Azure: every blob whose name starts with it)
   * @throws IllegalArgumentException {@code Invalid directory <directory>}
   */
  public List<ListItem> list(String directory) {
    checkDirectory(directory);
    return provider.list(config, userConfiguration, directory);
  }

  /**
   * Java addition (the TypeScript documentation lists {@code deleteFile} as AWS-only): removes a file.
   *
   * @param directory the directory
   * @param fileName the file name
   * @return {@code true} when the file was deleted, {@code false} when it did not exist
   * @throws IllegalArgumentException {@code Invalid directory <directory>}
   */
  public boolean deleteFile(String directory, String fileName) {
    checkDirectory(directory);
    return provider.deleteFile(config, userConfiguration, directory, fileName);
  }

  private void checkDirectory(String directory) {
    if (!Utils.isValidDirectory(directory, userConfiguration.directories())) {
      throw new IllegalArgumentException("Invalid directory " + directory);
    }
  }

  private static RocketDescriptor getRocketFromConfiguration(BoosterConfig config) {
    if (config.rockets != null) {
      for (RocketDescriptor rocket : config.rockets) {
        if (rocket != null && INFRA_PACKAGES.contains(rocket.packageName())) {
          return rocket;
        }
      }
    }
    throw new IllegalStateException(
        "Rocket not found. Please make sure you have setup the rocket packageName correctly");
  }
}
