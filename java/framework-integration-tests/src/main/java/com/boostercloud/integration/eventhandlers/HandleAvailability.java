package com.boostercloud.integration.eventhandlers;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.EventHandler;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.entities.Product;
import com.boostercloud.integration.events.ProductAvailabilityChanged;
import com.boostercloud.integration.events.StockMoved;

/**
 * Keeps the product availability in line with the stock movements ({@code src/event-handlers/handle-availability.ts}):
 * stock from {@code provider} adds units, stock to {@code customer} removes them, moves between warehouses change
 * nothing. Movements of unknown products are ignored.
 */
@EventHandler(StockMoved.class)
public class HandleAvailability {

  /**
   * @param event the movement
   * @param register the register
   * @throws IllegalStateException for {@link Constants#DISPATCH_EVENT_ERROR_CART_ID}
   */
  public static void handle(StockMoved event, Register register) {
    if (Constants.DISPATCH_EVENT_ERROR_CART_ID.equals(event.productID())) {
      throw new IllegalStateException(Constants.DISPATCH_ERROR_CART_MESSAGE);
    }
    event.getOrigin();
    Product product = Booster.entity(Product.class, UUID.of(event.productID())).orElse(null);
    if (product == null) {
      return;
    }
    product.getId();
    if ("provider".equals(event.origin())) {
      register.events(new ProductAvailabilityChanged(UUID.of(event.productID()), event.quantity()));
    } else if ("customer".equals(event.destination())) {
      register.events(new ProductAvailabilityChanged(UUID.of(event.productID()), -event.quantity()));
    }
  }
}
