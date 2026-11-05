package io.boostercloud.booster.migration;

import io.boostercloud.booster.core.BoosterException;
import java.time.Duration;
import java.time.Instant;

/** Event-sourced lifecycle, independent of the worker's lease and progress document. */
public record BoosterDataMigrationEntity(String id, DataMigrationStatus status, Instant lastUpdated,
    MigrationDuration duration, long attempts, String failure) {
  public record MigrationDuration(Instant start, Instant end, Long elapsedMilliseconds) {}

  public static BoosterDataMigrationEntity started(BoosterDataMigrationStarted event, BoosterDataMigrationEntity previous) {
    if (previous != null && previous.status() == DataMigrationStatus.FINISHED) return previous;
    Instant first = previous == null ? event.lastUpdated() : previous.duration().start();
    return new BoosterDataMigrationEntity(event.name(), DataMigrationStatus.RUNNING, event.lastUpdated(),
        new MigrationDuration(first, null, null), previous == null ? 1 : previous.attempts() + 1, null);
  }

  public static BoosterDataMigrationEntity finished(BoosterDataMigrationFinished event, BoosterDataMigrationEntity previous) {
    if (previous == null) throw new BoosterException(BoosterException.Code.VALIDATION, "A migration must start before it finishes");
    if (previous.status() == DataMigrationStatus.FINISHED) return previous;
    if (previous.status() != DataMigrationStatus.RUNNING)
      throw new BoosterException(BoosterException.Code.VALIDATION, "Resume a failed migration before finishing it");
    Instant start = previous.duration().start();
    Instant end = event.lastUpdated().isBefore(start) ? start : event.lastUpdated();
    return new BoosterDataMigrationEntity(event.name(), DataMigrationStatus.FINISHED, event.lastUpdated(),
        new MigrationDuration(start, end, Duration.between(start, end).toMillis()), previous.attempts(), null);
  }

  public static BoosterDataMigrationEntity failed(BoosterDataMigrationFailed event, BoosterDataMigrationEntity previous) {
    if (previous == null) throw new BoosterException(BoosterException.Code.VALIDATION, "A migration must start before it fails");
    if (previous.status() == DataMigrationStatus.FINISHED) return previous;
    return new BoosterDataMigrationEntity(event.name(), DataMigrationStatus.FAILED, event.lastUpdated(),
        previous.duration(), previous.attempts(), event.reason());
  }
}
