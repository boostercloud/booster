package com.boostercloud.rocket.fileuploads.azure.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.util.List;

/**
 * Port of {@code handlers/list.ts} (Azure; Java name {@code ListHandler} because {@code List} is taken).
 */
public final class ListHandler {

  private ListHandler() {}

  /**
   * {@code list(config, rocketFilesUserConfiguration, directory)}: the blobs whose name starts with the directory.
   *
   * @param config the application configuration
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory (a name prefix)
   * @return the blobs
   */
  public static List<ListItem> list(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory) {
    return new BlobService(config, rocketFilesUserConfiguration)
        .listBlobFolder(rocketFilesUserConfiguration.containerName(), directory);
  }
}
