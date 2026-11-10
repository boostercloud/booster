package com.boostercloud.framework.core.coreconcepts.datamigration.entities;

import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.types.concepts.BoosterDataMigrationEntityDuration;
import com.boostercloud.framework.types.concepts.DataMigrationStatus;

/**
 * Port of {@code core-concepts/data-migration/entities/booster-data-migration-entity.ts}: the built-in entity that
 * tracks the status of a data migration (id = the migration class name). Registered by {@code Booster.start} with a
 * deny-all event stream authorizer; its reducers are registered for {@code BoosterDataMigrationStarted}
 * ({@code started}) and {@code BoosterDataMigrationFinished} ({@code finished}). {@code status} is persisted as a
 * number ({@code 0}/{@code 1}).
 *
 * <p>The record shape is final; the two reducers are skeletons implemented by stage <b>core-2a</b> (spec 06 §3.11.4).
 *
 * @param id the data migration class name
 * @param status {@code RUNNING} (0) or {@code FINISHED} (1)
 * @param lastUpdated the {@code lastUpdated} of the last reduced event
 * @param duration when the migration ran, or {@code null}
 */
public record BoosterDataMigrationEntity(
    String id, DataMigrationStatus status, String lastUpdated, BoosterDataMigrationEntityDuration duration) {

  /**
   * Reducer of {@link BoosterDataMigrationStarted}: a {@code RUNNING} entity whose {@code duration.start} is the
   * reduction time. Implemented by stage core-2a.
   *
   * @param event the event
   * @param currentDataMigration the current snapshot, or {@code null}
   * @return the new snapshot
   */
  public static BoosterDataMigrationEntity started(
      BoosterDataMigrationStarted event, BoosterDataMigrationEntity currentDataMigration) {
    throw new UnsupportedOperationException("not implemented yet: BoosterDataMigrationEntity#started");
  }

  /**
   * Reducer of {@link BoosterDataMigrationFinished}: a {@code FINISHED} entity with the elapsed time when the current
   * snapshot has a {@code duration.start}. Implemented by stage core-2a.
   *
   * @param event the event
   * @param currentDataMigration the current snapshot
   * @return the new snapshot
   */
  public static BoosterDataMigrationEntity finished(
      BoosterDataMigrationFinished event, BoosterDataMigrationEntity currentDataMigration) {
    throw new UnsupportedOperationException("not implemented yet: BoosterDataMigrationEntity#finished");
  }
}
