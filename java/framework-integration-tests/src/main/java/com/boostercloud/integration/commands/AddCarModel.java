package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarModelAdded;

/**
 * Adds a car model ({@code src/commands/add-car-model.ts}).
 *
 * @param id the model
 * @param name its name
 * @param brand its brand
 */
@Command(authorize = All.class)
public record AddCarModel(UUID id, String name, String brand) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AddCarModel command, Register register) {
    register.events(new CarModelAdded(command.id(), command.name(), command.brand()));
  }
}
