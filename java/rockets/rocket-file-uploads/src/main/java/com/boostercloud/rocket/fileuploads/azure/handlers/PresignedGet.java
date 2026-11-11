package com.boostercloud.rocket.fileuploads.azure.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;

/**
 * Port of {@code handlers/presigned-get.ts} (Azure).
 */
public final class PresignedGet {

  private PresignedGet() {}

  /**
   * {@code presignedGet(config, rocketFilesUserConfiguration, directory, fileName)}: a read SAS URL of the blob.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return the URL
   */
  public static String presignedGet(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return new BlobService(config, rocketFilesUserConfiguration)
        .getBlobSasUrl(rocketFilesUserConfiguration.containerName(), directory, fileName);
  }
}
