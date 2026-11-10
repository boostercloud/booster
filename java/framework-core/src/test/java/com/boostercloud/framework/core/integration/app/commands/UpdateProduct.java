package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.events.ProductUpdated;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Renames and reprices a product (admins only).
 *
 * @param productId the product
 * @param displayName the new name
 * @param price the new price
 */
@Command(authorize = Admin.class)
public record UpdateProduct(UUID productId, String displayName, double price) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(UpdateProduct command, Register register) {
    register.events(new ProductUpdated(command.productId(), command.displayName(), command.price()));
  }
}
