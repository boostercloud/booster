package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.events.ShippingAddressUpdated;

/**
 * Sets the shipping address of a cart ({@code src/commands/update-shipping-address.ts}).
 *
 * @param cartId the cart
 * @param address the address
 */
@Command(authorize = All.class)
public record UpdateShippingAddress(UUID cartId, Address address) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(UpdateShippingAddress command, Register register) {
    register.events(new ShippingAddressUpdated(command.cartId(), command.address()));
  }
}
