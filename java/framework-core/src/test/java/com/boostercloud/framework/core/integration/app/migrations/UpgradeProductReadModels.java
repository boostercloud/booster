package com.boostercloud.framework.core.integration.app.migrations;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.ReadModelSchemaMigrator;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.decorators.DataMigration;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.types.concepts.Register;

/** Rewrites every stored product read model with the current schema. */
@DataMigration(order = 2)
public class UpgradeProductReadModels {
  /** How many read models the last run migrated. */
  public static volatile int migrated;

  /**
   * @param register the register of the migration
   */
  public static void start(Register register) {
    AppState.MIGRATIONS.add("UpgradeProductReadModels");
    migrated = new ReadModelSchemaMigrator(Booster.config()).migrateAll("ProductReadModel", 2);
    register.events(new BoosterDataMigrationFinished("UpgradeProductReadModels"));
  }
}
