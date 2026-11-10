package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;

/** A command without input that returns a string ({@code src/commands/empty-command.ts}). */
@Command(authorize = All.class)
public record EmptyCommand() {

  /**
   * @param command the command
   * @param register the register
   * @return {@code Empty command executed}
   */
  public static String handle(EmptyCommand command, Register register) {
    return "Empty command executed";
  }
}
