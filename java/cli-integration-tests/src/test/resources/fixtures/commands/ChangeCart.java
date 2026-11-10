package com.example.cartdemo.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;

@Command(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
public record ChangeCart() {
  public static void handle(ChangeCart command, Register register) {
    register.events(/* YOUR EVENT HERE */);
  }
}
