package com.boostercloud.rocket.fileuploads.azure.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;

/**
 * Java addition (the TypeScript documentation lists file deletion for Azure as a TODO): deletes a blob.
 */
public final class DeleteFile {

  private DeleteFile() {}

  /**
   * Deletes {@code <directory>/<fileName>} from the storage's container. Azure does not fire the blob trigger for a
   * deletion, so no event is stored.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return {@code true} when deleted, {@code false} when it did not exist
   */
  public static boolean deleteFile(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return new BlobService(config, rocketFilesUserConfiguration)
        .deleteBlob(rocketFilesUserConfiguration.containerName(), directory, fileName);
  }
}
