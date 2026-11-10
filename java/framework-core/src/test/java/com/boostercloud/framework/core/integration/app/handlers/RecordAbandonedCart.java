package com.boostercloud.framework.core.integration.app.handlers;

import com.boostercloud.framework.core.decorators.EventHandler;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.app.notifications.CartAbandoned;
import com.boostercloud.framework.types.concepts.Register;

/** Records the abandoned carts and who abandoned them. */
@EventHandler(CartAbandoned.class)
public class RecordAbandonedCart {
  /**
   * @param event the notification
   * @param register the register
   */
  public static void handle(CartAbandoned event, Register register) {
    AppState.ABANDONED_CARTS.add(event.cartId());
    AppState.ABANDONED_BY.add(register.currentUser() == null ? "nobody" : register.currentUser().username());
  }
}
