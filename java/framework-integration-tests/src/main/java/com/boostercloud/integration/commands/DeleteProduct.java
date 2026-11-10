package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.ProductDeleted;
import com.boostercloud.integration.roles.Admin;

/**
 * Soft-deletes a product ({@code src/commands/delete-product.ts}); its {@code ProductReadModel} is then deleted.
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
