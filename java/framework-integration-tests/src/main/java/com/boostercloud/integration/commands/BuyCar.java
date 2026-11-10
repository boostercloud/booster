package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.CarModel;
import com.boostercloud.integration.entities.CarOwner;
import com.boostercloud.integration.events.CarPurchased;

/**
 * Buys a car ({@code src/commands/buy-car.ts}): the event embeds the current owner and model snapshots ({@code null}
 * when they do not exist, as TypeScript's non-null assertion stores {@code undefined}).
 *
 * @param id the purchase
 * @param modelId the car model
 * @param ownerId the buyer
 */
@Command(authorize = All.class)
public record BuyCar(UUID id, UUID modelId, UUID ownerId) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(BuyCar command, Register register) {
    CarModel model = Booster.entity(CarModel.class, command.modelId()).orElse(null);
    CarOwner owner = Booster.entity(CarOwner.class, command.ownerId()).orElse(null);
    register.events(new CarPurchased(command.id(), owner, model));
  }
}
