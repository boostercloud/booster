package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarOwnerNameUpdated;

/**
 * Renames a car owner ({@code src/commands/update-car-owner-name.ts}); an owner renamed {@code SKIP} is not projected
 * into the purchases.
 *
 * @param id the owner
 * @param name the new name
 */
@Command(authorize = All.class)
public record UpdateCarOwnerName(UUID id, String name) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(UpdateCarOwnerName command, Register register) {
    register.events(new CarOwnerNameUpdated(command.id(), command.name()));
  }
}
