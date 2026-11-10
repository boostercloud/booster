package com.boostercloud.framework.core.services;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Port of {@code services/read-model-store.ts}: the write side of read models. For an entity snapshot it finds the
 * projections (un-projections when an event is deleted), the affected read models, runs the user projection methods
 * and stores, deletes or skips the results with optimistic concurrency and retries.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 07 §3.1). Projection methods are called with
 * {@code UserCode.invoke} so that {@code @Trace} on them works.
 */
public class ReadModelStore {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public ReadModelStore(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code project(entitySnapshotEnvelope)} ({@code deleteEvent = false}). Implemented by stage core-2b.
   *
   * @param entitySnapshotEnvelope the new snapshot
   */
  public void project(EntitySnapshotEnvelope entitySnapshotEnvelope) {
    project(entitySnapshotEnvelope, false);
  }

  /**
   * {@code project(entitySnapshotEnvelope, deleteEvent)}. Implemented by stage core-2b.
   *
   * @param entitySnapshotEnvelope the snapshot
   * @param deleteEvent {@code true} to run the un-projections (an event of the entity is being deleted)
   */
  public void project(EntitySnapshotEnvelope entitySnapshotEnvelope, boolean deleteEvent) {
    throw new UnsupportedOperationException("not implemented yet: ReadModelStore#project");
  }

  /**
   * {@code getReadModels(entityInstance, projectionMetadata, entityMetadata)}: the read models matched by a
   * projection's join key. Implemented by stage core-2b.
   *
   * @param entityInstance the entity
   * @param projectionMetadata the projection
   * @param entityMetadata the entity metadata
   * @return the read model instances
   */
  public List<Object> getReadModels(Object entityInstance, ProjectionMetadata projectionMetadata, EntityMetadata entityMetadata) {
    throw new UnsupportedOperationException("not implemented yet: ReadModelStore#getReadModels");
  }

  /**
   * {@code fetchReadModel(readModelName, readModelID, sequenceKey?)}. Implemented by stage core-2b.
   *
   * @param readModelName the read model class name
   * @param readModelID the id, or {@code null}
   * @param sequenceKey the sequence key, or {@code null}
   * @return the (migrated) read model instance, if found
   */
  public Optional<Object> fetchReadModel(String readModelName, UUID readModelID, SequenceKey sequenceKey) {
    throw new UnsupportedOperationException("not implemented yet: ReadModelStore#fetchReadModel");
  }

  /**
   * {@code getProjectionFunction(projectionMetadata)}: the static projection method. Implemented by stage core-2b.
   *
   * @param projectionMetadata the projection
   * @return the method
   */
  public Method getProjectionFunction(ProjectionMetadata projectionMetadata) {
    throw new UnsupportedOperationException("not implemented yet: ReadModelStore#getProjectionFunction");
  }
}
