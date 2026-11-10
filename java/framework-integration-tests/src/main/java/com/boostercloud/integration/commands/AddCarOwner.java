package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarOwnerAdded;

/**
 * Adds a car owner ({@code src/commands/add-car-owner.ts}).
 *
 * @param id the owner
 * @param name the owner's name
 */
@Command(authorize = All.class)
public record AddCarOwner(UUID id, String name) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AddCarOwner command, Register register) {
    register.events(new CarOwnerAdded(command.id(), command.name()));
  }
}
