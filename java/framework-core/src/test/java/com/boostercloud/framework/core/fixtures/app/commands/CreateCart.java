package com.boostercloud.framework.core.fixtures.app.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.fixtures.app.domain.CartCreated;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

/** A command of the fixture application. */
@Command(authorize = All.class)
public record CreateCart(UUID cartId) {
  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(CreateCart command, Register register) {
    register.events(new CartCreated(command.cartId()));
  }
}
