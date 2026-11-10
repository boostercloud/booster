package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.coreconcepts.datamigration.entities.BoosterDataMigrationEntity;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.BoosterDataMigrationEntityDuration;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.integration.common.Nullable;

/**
 * The state of the data migrations ({@code src/read-models/data-migrations-read-model.ts}), projected from the
 * framework's own {@code BoosterDataMigrationEntity}. {@code status} is the numeric enum as a string ({@code "0"}
 * running, {@code "1"} finished), as TypeScript's {@code status.toString()}.
 *
 * @param id the data migration class name
 * @param status {@code "0"} or {@code "1"}
 * @param lastUpdated when it last changed
 * @param duration when it ran
 */
@ReadModel(authorize = All.class)
public record DataMigrationsReadModel(
    String id, String status, String lastUpdated, @Nullable BoosterDataMigrationEntityDuration duration) {

  /**
   * @param migration the data migration entity
   * @param oldMigration the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = BoosterDataMigrationEntity.class, joinKey = "id")
  public static ProjectionResult<DataMigrationsReadModel> updated(
      BoosterDataMigrationEntity migration, DataMigrationsReadModel oldMigration) {
    return ProjectionResult.of(new DataMigrationsReadModel(migration.id(), migration.status().toString(),
        migration.lastUpdated(), migration.duration()));
  }
}
