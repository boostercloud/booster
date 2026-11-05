package io.boostercloud.booster.migration;

import java.time.Instant;
import java.util.Objects;

/** A migration finishes only when this event is committed, never merely when start returns. */
public record BoosterDataMigrationFinished(String name, Instant lastUpdated) {
  public BoosterDataMigrationFinished { Objects.requireNonNull(name); Objects.requireNonNull(lastUpdated); }
  public BoosterDataMigrationFinished(String name) { this(name, Instant.now()); }
}
