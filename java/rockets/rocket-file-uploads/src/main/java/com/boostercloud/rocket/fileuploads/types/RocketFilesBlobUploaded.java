package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.types.concepts.EventInterface;

/**
 * The shape of the stored event value ({@code RocketFilesBlobUploaded} of {@code rocket-files-blob-uploaded.ts}):
 * {@code { metadata }}. {@link UploadedFileEvent} implements it.
 */
public interface RocketFilesBlobUploaded extends EventInterface {

  /**
   * @return the provider metadata of the upload
   */
  Object metadata();
}
