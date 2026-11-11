package com.example.store.eventhandlers;

import com.boostercloud.framework.core.decorators.EventHandler;
import com.boostercloud.framework.core.decorators.GlobalEventHandler;
import com.boostercloud.framework.types.concepts.Register;
import com.example.store.events.CartItemChanged;

@EventHandler(CartItemChanged.class)
public class LogCartItemChanged {
  public static void handle(CartItemChanged event, Register register) {}

  @GlobalEventHandler
  public static class LogAll {
    public static void handle(Object event, Register register) {}
  }
}
