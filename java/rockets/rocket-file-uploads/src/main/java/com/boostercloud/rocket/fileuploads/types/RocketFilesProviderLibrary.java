package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * What a provider runtime library offers to {@code FileHandler} ({@code RocketFilesProviderLibrary} of
 * {@code rocket-files-provider-library.ts}). The directory has already been validated against the configuration's
 * patterns; the file name is never validated. TypeScript promises are blocking calls.
 */
public interface RocketFilesProviderLibrary {

  /**
   * {@code presignedGet(config, rocketFilesUserConfiguration, directory, fileName)}.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return a URL the file can be downloaded from
   */
  String presignedGet(
      BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration, String directory, String fileName);

  /**
   * {@code presignedPut(config, rocketFilesUserConfiguration, directory, fileName)} ({@code Promise<unknown>}: both
   * providers of this rocket answer a URL string).
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return what the client needs to upload the file (a URL string for Azure and Local)
   */
  Object presignedPut(
      BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration, String directory, String fileName);

  /**
   * {@code list(config, rocketFilesUserConfiguration, directory)}.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @return the files (provider-specific depth and properties)
   */
  List<ListItem> list(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration, String directory);

  /**
   * Java addition (the TypeScript documentation describes {@code deleteFile} for AWS only and lists it as a TODO for
   * Azure and Local): removes one file.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return {@code true} when the file was deleted, {@code false} when it did not exist
   * @throws UnsupportedOperationException when the library does not support deleting files
   */
  default boolean deleteFile(
      BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration, String directory,
      String fileName) {
    throw new UnsupportedOperationException("deleteFile is not supported by " + getClass().getName());
  }
}
