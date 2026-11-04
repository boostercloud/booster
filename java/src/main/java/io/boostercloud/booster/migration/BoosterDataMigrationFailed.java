package io.boostercloud.booster.migration;

import java.time.Instant;
import java.util.Objects;

public record BoosterDataMigrationFailed(String name, Instant lastUpdated, String reason) {
  public BoosterDataMigrationFailed {
    Objects.requireNonNull(name); Objects.requireNonNull(lastUpdated); Objects.requireNonNull(reason);
  }
}
