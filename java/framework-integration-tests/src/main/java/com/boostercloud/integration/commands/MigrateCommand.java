package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.BoosterDataMigrations;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;

/**
 * Runs the pending data migrations ({@code src/commands/migrate-command.ts}); nothing else starts them.
 */
@Command(authorize = All.class)
public record MigrateCommand() {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(MigrateCommand command, Register register) {
    System.out.println("migrating");
    BoosterDataMigrations.run();
    System.out.println("migrated");
  }
}
