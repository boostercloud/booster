package com.boostercloud.rocket.fileuploads.local;

import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;

/**
 * Port of {@code file-uploaded.ts} (local): the metadata of a notification of the local file watcher.
 */
public final class FileUploaded {

  private FileUploaded() {}

  /**
   * {@code getMetadataFromRequest(request)}: the request itself, which the watcher builds as
   * {@code { BOOSTER_ROCKET_FUNCTION_ID: 'rocket-files', uri, name }} (TypeScript declares it as
   * {@code LocalBindingData { name, eventType }}, which does not match what is sent).
   *
   * @param request the rocket function request
   * @return the same object
   */
  public static Object getMetadataFromRequest(Object request) {
    return request;
  }

  /**
   * {@code validateMetadata(configuration, metadata)}: always {@code true}, so every user configuration stores an
   * event for every notification (TypeScript behaviour).
   *
   * @param configuration ignored
   * @param metadata ignored
   * @return {@code true}
   */
  public static boolean validateMetadata(RocketFilesUserConfiguration configuration, Object metadata) {
    return true;
  }
}
