package com.example.cartdemo.eventhandlers;

import com.boostercloud.framework.core.decorators.EventHandler;
import com.boostercloud.framework.types.concepts.Register;
import com.example.cartdemo.events.CartItemChanged;

@EventHandler(CartItemChanged.class)
public class HandleCartChange {
  public static void handle(CartItemChanged event, Register register) {}
}
