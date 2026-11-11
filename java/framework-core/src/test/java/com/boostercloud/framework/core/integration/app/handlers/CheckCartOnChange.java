package com.boostercloud.framework.core.integration.app.handlers;

import com.boostercloud.framework.core.decorators.EventHandler;
import com.boostercloud.framework.core.integration.app.events.CartChecked;
import com.boostercloud.framework.core.integration.app.events.CartItemChanged;
import com.boostercloud.framework.types.concepts.Register;

/** Every cart change registers a check of the cart; a quantity of 13 makes the handler fail. */
@EventHandler(CartItemChanged.class)
public class CheckCartOnChange {
  /**
   * @param event the change
   * @param register the register
   */
  public static void handle(CartItemChanged event, Register register) {
    if (event.quantity() == 13) {
      throw new IllegalStateException("Unlucky quantity for cart " + event.cartId());
    }
    register.events(new CartChecked(event.cartId()));
  }
}
