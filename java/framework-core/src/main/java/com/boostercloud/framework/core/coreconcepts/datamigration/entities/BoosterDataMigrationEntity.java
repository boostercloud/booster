package com.boostercloud.framework.core.coreconcepts.datamigration.entities;

import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.BoosterDataMigrationEntityDuration;
import com.boostercloud.framework.types.concepts.DataMigrationStatus;
import java.time.Instant;

/**
 * Port of {@code core-concepts/data-migration/entities/booster-data-migration-entity.ts}: the built-in entity that
 * tracks the status of a data migration (id = the migration class name). Registered by {@code Booster.start} with a
 * deny-all event stream authorizer; its reducers are registered for {@code BoosterDataMigrationStarted}
 * ({@code started}) and {@code BoosterDataMigrationFinished} ({@code finished}). {@code status} is persisted as a
 * number ({@code 0}/{@code 1}).
 *
 * <p>Both reducers read the <b>wall clock at reduction time</b> ({@link Dates#now()}), not the time of the event, as
 * TypeScript does: the duration reflects when the reductions ran and changes if the stream is reduced again from
 * scratch.
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
   * reduction time. The current state is ignored.
   *
   * @param event the event
   * @param currentDataMigration the current snapshot, or {@code null}
   * @return the new snapshot
   */
  public static BoosterDataMigrationEntity started(
      BoosterDataMigrationStarted event, BoosterDataMigrationEntity currentDataMigration) {
    BoosterDataMigrationEntityDuration duration = new BoosterDataMigrationEntityDuration(Dates.nowIso(), null, null);
    return new BoosterDataMigrationEntity(event.name(), DataMigrationStatus.RUNNING, event.lastUpdated(), duration);
  }

  /**
   * Reducer of {@link BoosterDataMigrationFinished}: a {@code FINISHED} entity. When the current snapshot has a
   * {@code duration.start} (a non-empty string), the duration gets {@code end} = the reduction time and
   * {@code elapsedMilliseconds} = {@code end - start} ({@code null} when {@code start} is not a date: JavaScript's
   * {@code NaN} is written as {@code null}); otherwise the result has no duration.
   *
   * @param event the event
   * @param currentDataMigration the current snapshot
   * @return the new snapshot
   * @throws NullPointerException when there is no current snapshot (a {@code Finished} event without a previous
   *     {@code Started} one; TypeScript fails with a {@code TypeError} too), which the event store handles as a reducer
   *     error
   */
  public static BoosterDataMigrationEntity finished(
      BoosterDataMigrationFinished event, BoosterDataMigrationEntity currentDataMigration) {
    if (currentDataMigration == null) {
      throw new NullPointerException("Cannot read the duration of the data migration " + event.name()
          + ": it has no previous state (BoosterDataMigrationFinished was reduced without BoosterDataMigrationStarted)");
    }
    Instant current = Dates.now();
    BoosterDataMigrationEntityDuration currentDuration = currentDataMigration.duration();
    String start = currentDuration == null ? null : currentDuration.start();
    if (start != null && !start.isEmpty()) {
      String end = Dates.toIso(current);
      Long elapsedTime = elapsedMilliseconds(start, current);
      BoosterDataMigrationEntityDuration duration = new BoosterDataMigrationEntityDuration(start, end, elapsedTime);
      return new BoosterDataMigrationEntity(event.name(), DataMigrationStatus.FINISHED, event.lastUpdated(), duration);
    }
    return new BoosterDataMigrationEntity(event.name(), DataMigrationStatus.FINISHED, event.lastUpdated(), null);
  }

  /** {@code current.getTime() - Date.parse(start)}; {@code null} for {@code NaN}. */
  private static Long elapsedMilliseconds(String start, Instant current) {
    try {
      return current.toEpochMilli() - Dates.toEpochMillis(start);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
