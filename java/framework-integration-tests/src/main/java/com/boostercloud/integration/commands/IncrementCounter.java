package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CounterAdded;

/**
 * Increments a {@code Counter} ({@code src/commands/increment-counter.ts}).
 *
 * @param counterId the counter
 * @param identifier a label
 */
@Command(authorize = All.class)
public record IncrementCounter(UUID counterId, String identifier) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(IncrementCounter command, Register register) {
    register.events(new CounterAdded(command.counterId(), command.identifier()));
  }
}
