package com.boostercloud.rocket.fileuploads.e2e.app;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.rocket.fileuploads.types.UploadedFileEntity;

/**
 * The README's read model of the uploaded files.
 *
 * @param id the md5 of the upload metadata
 * @param metadata the upload metadata
 */
@ReadModel(authorize = All.class)
public record UploadedFileEntityReadModel(UUID id, Object metadata) {
  /**
   * @param entity the entity
   * @param current the current read model
   * @return the new read model
   */
  @Projects(entity = UploadedFileEntity.class, joinKey = "id")
  public static ProjectionResult<UploadedFileEntityReadModel> projectUploadedFileEntity(UploadedFileEntity entity,
      UploadedFileEntityReadModel current) {
    return ProjectionResult.of(new UploadedFileEntityReadModel(entity.id(), entity.metadata()));
  }
}
