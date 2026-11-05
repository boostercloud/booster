package io.boostercloud.booster.migration;

import java.time.Instant;
import java.util.Objects;

public record BoosterDataMigrationStarted(String name, Instant lastUpdated) {
  public BoosterDataMigrationStarted { Objects.requireNonNull(name); Objects.requireNonNull(lastUpdated); }
  public BoosterDataMigrationStarted(String name) { this(name, Instant.now()); }
}
