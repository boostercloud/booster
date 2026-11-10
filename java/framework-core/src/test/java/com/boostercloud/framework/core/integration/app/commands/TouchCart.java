package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.BoosterTouchEntityHandler;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Touches a cart so that its read models are projected again (admins only).
 *
 * @param cartId the cart
 */
@Command(authorize = Admin.class)
public record TouchCart(UUID cartId) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(TouchCart command, Register register) {
    BoosterTouchEntityHandler.touchEntity("Cart", command.cartId());
  }
}
