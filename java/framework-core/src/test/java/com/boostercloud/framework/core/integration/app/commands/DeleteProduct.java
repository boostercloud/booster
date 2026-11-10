package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.events.ProductDeleted;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Withdraws a product (admins only).
 *
 * @param productId the product
 */
@Command(authorize = Admin.class)
public record DeleteProduct(UUID productId) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(DeleteProduct command, Register register) {
    register.events(new ProductDeleted(command.productId()));
  }
}
