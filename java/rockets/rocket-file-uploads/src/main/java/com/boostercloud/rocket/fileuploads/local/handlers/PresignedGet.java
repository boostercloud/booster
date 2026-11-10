package com.boostercloud.rocket.fileuploads.local.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;

/**
 * Port of {@code handlers/presigned-get.ts} (local).
 */
public final class PresignedGet {

  private PresignedGet() {}

  /**
   * {@code presignedGet(_config, rocketFilesUserConfiguration, directory, fileName)}: the URL the local server serves
   * the file at, {@code http://localhost:<ROCKET_FILES_LOCAL_PORT or 3000>/<storage>/<container>/<directory>/<file>}
   * (nothing signed or encoded, no expiry; the same URL as {@link PresignedPut}).
   *
   * @param config ignored
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory, inserted as is
   * @param fileName the file name, inserted as is
   * @return the URL
   */
  public static String presignedGet(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return localUrl(rocketFilesUserConfiguration, directory, fileName);
  }

  static String localUrl(RocketFilesUserConfiguration configuration, String directory, String fileName) {
    return "http://localhost:" + Constants.localPort() + "/" + configuration.storageName() + "/"
        + configuration.containerName() + "/" + directory + "/" + fileName;
  }
}
