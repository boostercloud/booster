package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.notifications.CartAbandoned;
import com.boostercloud.framework.core.integration.app.roles.Customer;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A customer abandons a cart: registers a notification.
 *
 * @param cartId the cart
 */
@Command(authorize = Customer.class)
public record AbandonCart(UUID cartId) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(AbandonCart command, Register register) {
    register.events(new CartAbandoned(command.cartId()));
  }
}
