package com.boostercloud.rocket.fileuploads.local.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;

/**
 * Port of {@code handlers/presigned-put.ts} (local).
 */
public final class PresignedPut {

  private PresignedPut() {}

  /**
   * {@code presignedPut(_config, rocketFilesUserConfiguration, directory, fileName)}: the URL the local server accepts
   * the upload at ({@code PUT} with the raw content); the same URL as {@link PresignedGet}.
   *
   * @param config ignored
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory, inserted as is
   * @param fileName the file name, inserted as is
   * @return the URL
   */
  public static String presignedPut(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    return PresignedGet.localUrl(rocketFilesUserConfiguration, directory, fileName);
  }
}
