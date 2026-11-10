package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Stock;
import com.boostercloud.integration.events.ErrorEvent;
import com.boostercloud.integration.events.StockMoved;
import com.boostercloud.integration.roles.Admin;

/**
 * Moves units of a product between warehouses ({@code src/commands/move-stock.ts}); {@code provider} as origin
 * brings new stock in, {@code customer} as destination sells it.
 *
 * <p>As in TypeScript, the "not enough stock" branch registers an {@link ErrorEvent} that no entity reduces and that is
 * not a notification, so storing the register fails ({@code Couldn't find information about event ErrorEvent...}) and
 * the mutation reports that error.
 *
 * @param productID the product
 * @param origin where the units come from
 * @param destination where they go
 * @param quantity how many units
 */
@Command(authorize = Admin.class)
public record MoveStock(String productID, String origin, String destination, double quantity) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(MoveStock command, Register register) {
    Stock stock = Booster.entity(Stock.class, UUID.of(command.productID())).orElse(null);
    if (command.enoughStock(stock)) {
      register.events(new StockMoved(command.productID(), command.origin(), command.destination(), command.quantity()));
    } else {
      register.events(new ErrorEvent(UUID.of("MoveStock-" + command.productID()),
          "There is not enough stock to perform command operation", command));
    }
  }

  /** An instance method on purpose: the framework must hand the handler a real {@code MoveStock}. */
  private boolean enoughStock(Stock stock) {
    if ("provider".equals(origin)) {
      return true;
    }
    if (stock == null) {
      return false;
    }
    Double count = stock.warehouses() == null ? null : stock.warehouses().get(origin);
    // undefined >= n and NaN >= n are false
    return count != null && count >= quantity;
  }
}
