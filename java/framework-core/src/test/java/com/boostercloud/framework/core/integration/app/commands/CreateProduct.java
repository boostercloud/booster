package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.events.ProductCreated;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Adds a product to the catalogue (admins only) and returns its id.
 *
 * @param sku the stock keeping unit
 * @param displayName the name
 * @param price the price
 * @param category the category
 */
@Command(authorize = Admin.class)
public record CreateProduct(String sku, String displayName, double price, String category) {
  /**
   * @param command the command
   * @param register the register
   * @return the new product id
   */
  public static UUID handle(CreateProduct command, Register register) {
    UUID productId = UUID.generate();
    register.events(new ProductCreated(productId, command.sku(), command.displayName(), command.price(),
        command.category()));
    return productId;
  }
}
