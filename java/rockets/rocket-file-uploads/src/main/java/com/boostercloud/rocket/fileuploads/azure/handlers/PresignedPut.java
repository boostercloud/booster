package com.boostercloud.rocket.fileuploads.azure.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;

/**
 * Port of {@code handlers/presigned-put.ts} (Azure).
 */
public final class PresignedPut {

  /** {@code WRITE_PERMISSION}. */
  public static final String WRITE_PERMISSION = "w";

  private PresignedPut() {}

  /**
   * {@code presignedPut(config, rocketFilesUserConfiguration, directory, fileName)}: a write SAS URL of the blob; the
   * client uploads with {@code PUT <url>} and {@code x-ms-blob-type: BlockBlob} (Azure answers 201).
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return the URL
   */
  public static String presignedPut(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return new BlobService(config, rocketFilesUserConfiguration).getBlobSasUrl(
        rocketFilesUserConfiguration.containerName(), directory, fileName, WRITE_PERMISSION,
        BlobService.DEFAULT_EXPIRES_ON_SECONDS);
  }
}
