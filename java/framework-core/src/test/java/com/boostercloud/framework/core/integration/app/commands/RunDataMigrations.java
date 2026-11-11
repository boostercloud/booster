package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.BoosterDataMigrations;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.Register;

/** Runs the pending data migrations (admins only); returns whether one was started or is running. */
@Command(authorize = Admin.class)
public record RunDataMigrations() {
  /**
   * @param command the command
   * @param register the register
   * @return {@code BoosterDataMigrations.run()}
   */
  public static boolean handle(RunDataMigrations command, Register register) {
    return BoosterDataMigrations.run();
  }
}
