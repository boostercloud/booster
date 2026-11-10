package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;

/**
 * Context of an error thrown by a projection; hook
 * {@code onProjectionError(error, entityEnvelope, projectionMetadata, entity, readModel)} (note the different
 * argument order). Port of {@code ProjectionGlobalError}; the original error is the last constructor argument.
 */
public final class ProjectionGlobalError extends GlobalErrorContainer {
  private final EntitySnapshotEnvelope entityEnvelope;
  private final Object entity;
  private final Object readModel;
  private final ProjectionMetadata projectionMetadata;

  /**
   * @param entityEnvelope the entity snapshot
   * @param entity the entity instance
   * @param readModel the current read model, or {@code null}
   * @param projectionMetadata the projection
   * @param originalError the error that happened
   */
  public ProjectionGlobalError(EntitySnapshotEnvelope entityEnvelope, Object entity, Object readModel, ProjectionMetadata projectionMetadata, Throwable originalError) {
    super(originalError);
    this.entityEnvelope = entityEnvelope;
    this.entity = entity;
    this.readModel = readModel;
    this.projectionMetadata = projectionMetadata;
  }

  /**
   * @return the entity snapshot
   */
  public EntitySnapshotEnvelope entityEnvelope() {
    return entityEnvelope;
  }

  /**
   * @return the entity instance
   */
  public Object entity() {
    return entity;
  }

  /**
   * @return the current read model, or {@code null}
   */
  public Object readModel() {
    return readModel;
  }

  /**
   * @return the projection
   */
  public ProjectionMetadata projectionMetadata() {
    return projectionMetadata;
  }
}
