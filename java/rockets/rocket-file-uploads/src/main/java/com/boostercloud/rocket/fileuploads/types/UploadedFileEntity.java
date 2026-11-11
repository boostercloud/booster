package com.boostercloud.rocket.fileuploads.types;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The entity of the uploaded files ({@code UploadedFileEntity} of {@code uploaded-file-entity.ts}). Applications
 * project it into their own read models ({@code @Projects(entity = UploadedFileEntity.class, joinKey = "id")}).
 * The event stream is not readable through GraphQL ({@code @Entity} without {@code authorizeReadEvents}, as in
 * TypeScript).
 *
 * @param id the MD5 of the upload metadata ({@link UploadedFileEvent#entityID()})
 * @param metadata the provider metadata of the upload
 */
@Entity
public record UploadedFileEntity(UUID id, Object metadata) {

  /**
   * {@code reduceUploadedFileEntity(event)}: every upload replaces the state ({@code current} is ignored, as the
   * TypeScript reducer does not even declare it).
   *
   * @param event the upload event
   * @param current the current state, ignored
   * @return the new state
   */
  @Reduces(UploadedFileEvent.class)
  public static UploadedFileEntity reduceUploadedFileEntity(UploadedFileEvent event, UploadedFileEntity current) {
    return new UploadedFileEntity(event.entityID(), event.metadata());
  }
}
