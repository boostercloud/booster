package com.boostercloud.rocket.fileuploads.azure.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import com.boostercloud.rocket.fileuploads.types.Utils;

/**
 * Java addition (the TypeScript documentation lists file deletion for Azure as a TODO): deletes a blob.
 */
public final class DeleteFile {

  private DeleteFile() {}

  /**
   * Deletes {@code <directory>/<fileName>} from the storage's container. Azure does not fire the blob trigger for a
   * deletion, so no event is stored. The file name must be a plain name ({@link Utils#isPlainFileName}): the blob
   * must be in {@code directory}, the folder {@code FileHandler} checked against the patterns.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name, without folders
   * @return {@code true} when deleted, {@code false} when it did not exist
   * @throws IllegalArgumentException {@code Invalid file name <fileName>}, before any request
   */
  public static boolean deleteFile(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    if (!Utils.isPlainFileName(fileName)) {
      throw new IllegalArgumentException("Invalid file name " + fileName);
    }
    return new BlobService(config, rocketFilesUserConfiguration)
        .deleteBlob(rocketFilesUserConfiguration.containerName(), directory, fileName);
  }
}
