package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarModelNameUpdated;

/**
 * Renames a car model ({@code src/commands/update-car-model-name.ts}); every purchase of the model is re-projected
 * through the read-model join key of {@code CarPurchasesReadModel}.
 *
 * @param id the model
 * @param name the new name
 */
@Command(authorize = All.class)
public record UpdateCarModelName(UUID id, String name) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(UpdateCarModelName command, Register register) {
    register.events(new CarModelNameUpdated(command.id(), command.name()));
  }
}
