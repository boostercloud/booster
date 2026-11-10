package com.boostercloud.framework.core.coreconcepts.datamigration.events;

import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code core-concepts/data-migration/events/booster-data-migration-started.ts}: emitted by
 * {@code BoosterDataMigrations} before a data migration starts. Reduced by
 * {@code BoosterDataMigrationEntity.started}.
 *
 * @param name the data migration class name (the id of its {@code BoosterDataMigrationEntity})
 * @param lastUpdated ISO-8601 timestamp; {@code null} means now
 */
public record BoosterDataMigrationStarted(String name, String lastUpdated) {

  /**
   * @param name the data migration class name
   * @param lastUpdated ISO-8601 timestamp; {@code null} means now ({@code Dates.nowIso()})
   */
  public BoosterDataMigrationStarted {
    lastUpdated = lastUpdated == null ? Dates.nowIso() : lastUpdated;
  }

  /**
   * @param name the data migration class name; {@code lastUpdated} is now
   */
  public BoosterDataMigrationStarted(String name) {
    this(name, null);
  }

  /**
   * @return the id of the entity the event belongs to ({@link #name()})
   */
  public UUID entityID() {
    return UUID.ofNullable(name);
  }
}
