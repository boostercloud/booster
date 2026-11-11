package com.boostercloud.integration.eventhandlers;

import com.boostercloud.framework.core.decorators.EventHandler;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CartChecked;
import com.boostercloud.integration.notifications.CartAbandoned;

/**
 * Checks the cart of every {@link CartAbandoned} notification ({@code src/event-handlers/handle-abandoned-cart.ts}).
 */
@EventHandler(CartAbandoned.class)
public class HandleCartAbandoned {

  /**
   * @param event the notification (its partition key is the cart id)
   * @param register the register
   */
  public static void handle(CartAbandoned event, Register register) {
    System.out.println("CART ABANDONED!!! " + BoosterJson.toJson(event));
    register.events(new CartChecked(UUID.of(event.something())));
  }
}
