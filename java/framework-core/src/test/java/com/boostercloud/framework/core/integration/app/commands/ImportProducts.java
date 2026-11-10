package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.integration.app.events.ProductCreated;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Creates {@code count} products of one category in one go (admins only): their projections into the category read
 * model run concurrently.
 *
 * @param category the category
 * @param count how many products
 */
@Command(authorize = Admin.class)
public record ImportProducts(String category, int count) {
  /**
   * @param command the command
   * @param register the register
   * @return how many products were registered
   */
  public static int handle(ImportProducts command, Register register) {
    for (int i = 0; i < command.count(); i++) {
      register.events(new ProductCreated(UUID.generate(), "IMP-" + i, "Imported " + i, 10 + i, command.category()));
    }
    return command.count();
  }
}
