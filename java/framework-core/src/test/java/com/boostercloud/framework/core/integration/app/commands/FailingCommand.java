package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;

/**
 * Always fails with {@code reason}; the global error handler decides what the client sees.
 *
 * @param reason the failure message
 */
@Command(authorize = All.class)
public record FailingCommand(String reason) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(FailingCommand command, Register register) {
    throw new IllegalStateException(command.reason());
  }
}
