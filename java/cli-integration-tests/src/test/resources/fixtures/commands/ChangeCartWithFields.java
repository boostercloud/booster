package com.example.cartdemo.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;

@Command(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
public record ChangeCartWithFields(
    UUID cartId,
    String sku,
    double quantity
) {
  public static void handle(ChangeCartWithFields command, Register register) {
    register.events(/* YOUR EVENT HERE */);
  }
}
