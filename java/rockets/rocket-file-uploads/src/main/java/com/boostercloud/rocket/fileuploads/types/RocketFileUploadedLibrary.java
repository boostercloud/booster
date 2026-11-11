package com.boostercloud.rocket.fileuploads.types;

/**
 * What a provider runtime library offers to the rocket function ({@code RocketFileUploadedLibrary} of
 * {@code rocket-file-uploaded-library.ts}): the metadata of an upload notification and whether it belongs to a user
 * configuration.
 */
public interface RocketFileUploadedLibrary {

  /**
   * {@code getMetadataFromRequest(request)}.
   *
   * @param request the raw rocket function request
   * @return the metadata stored in the {@code UploadedFileEvent}
   */
  Object getMetadataFromRequest(Object request);

  /**
   * {@code validateMetadata(configuration, metadata)}.
   *
   * @param configuration a user configuration
   * @param metadata what {@link #getMetadataFromRequest(Object)} returned
   * @return whether an event must be stored for that configuration
   */
  boolean validateMetadata(RocketFilesUserConfiguration configuration, Object metadata);
}
