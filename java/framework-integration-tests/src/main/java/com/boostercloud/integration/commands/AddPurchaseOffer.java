package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarOfferAdded;
import java.util.List;

/**
 * Adds an offer applying to some purchases ({@code src/commands/add-purchase-offer.ts}); the command's
 * {@code purchaseIds} become the event's {@code purchasesIds}.
 *
 * @param id the offer
 * @param name its name
 * @param purchaseIds the purchases it applies to
 */
@Command(authorize = All.class)
public record AddPurchaseOffer(UUID id, String name, List<String> purchaseIds) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AddPurchaseOffer command, Register register) {
    register.events(new CarOfferAdded(command.id(), command.name(), command.purchaseIds()));
  }
}
