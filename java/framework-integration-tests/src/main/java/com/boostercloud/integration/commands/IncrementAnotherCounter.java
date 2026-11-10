package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.AnotherCounterAdded;

/**
 * Increments an {@code AnotherCounter} ({@code src/commands/increment-another-counter.ts}).
 *
 * @param anotherCounterId the counter
 * @param identifier a label
 */
@Command(authorize = All.class)
public record IncrementAnotherCounter(UUID anotherCounterId, String identifier) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(IncrementAnotherCounter command, Register register) {
    register.events(new AnotherCounterAdded(command.anotherCounterId(), command.identifier()));
  }
}
