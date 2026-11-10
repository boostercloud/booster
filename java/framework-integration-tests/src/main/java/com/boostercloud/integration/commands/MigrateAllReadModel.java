package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.ReadModelSchemaMigrator;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;

/**
 * Migrates every stored instance of a read model to its current schema version
 * ({@code src/commands/migrate-all-read-model.ts}); an unknown read model fails with the framework's
 * {@code Resource Not Found} error.
 *
 * @param readModelName the read model type
 */
@Command(authorize = All.class)
public record MigrateAllReadModel(String readModelName) {

  /**
   * @param command the command
   * @param register the register
   * @return {@code Migrated <count> <readModelName>}
   */
  public static String handle(MigrateAllReadModel command, Register register) {
    ReadModelSchemaMigrator readModelSchemaMigrator = new ReadModelSchemaMigrator(Booster.config());
    int result = readModelSchemaMigrator.migrateAll(command.readModelName());
    return "Migrated " + result + " " + command.readModelName();
  }
}
