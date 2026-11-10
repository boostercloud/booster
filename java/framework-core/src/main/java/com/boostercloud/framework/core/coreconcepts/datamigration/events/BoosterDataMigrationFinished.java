package com.boostercloud.framework.core.coreconcepts.datamigration.events;

import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code core-concepts/data-migration/events/booster-data-migration-finished.ts}: emitted <b>by user code</b>
 * at the end of a data migration ({@code register.events(new BoosterDataMigrationFinished("MyMigration"))}). Reduced
 * by {@code BoosterDataMigrationEntity.finished}.
 *
 * @param name the data migration class name
 * @param lastUpdated ISO-8601 timestamp; {@code null} means now
 */
public record BoosterDataMigrationFinished(String name, String lastUpdated) {

  /**
   * @param name the data migration class name
   * @param lastUpdated ISO-8601 timestamp; {@code null} means now ({@code Dates.nowIso()})
   */
  public BoosterDataMigrationFinished {
    lastUpdated = lastUpdated == null ? Dates.nowIso() : lastUpdated;
  }

  /**
   * @param name the data migration class name; {@code lastUpdated} is now
   */
  public BoosterDataMigrationFinished(String name) {
    this(name, null);
  }

  /**
   * @return the id of the entity the event belongs to ({@link #name()})
   */
  public UUID entityID() {
    return UUID.ofNullable(name);
  }
}
