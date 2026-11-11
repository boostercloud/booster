package com.example.cartdemo.scheduledcommands;

import com.boostercloud.framework.core.decorators.ScheduledCommand;
import com.boostercloud.framework.types.concepts.Register;

@ScheduledCommand(
    // Specify schedule settings here. By default, it will be triggered every 30 minutes
    minute = "0/30")
public class CheckCart {
  public static void handle(Register register) {
    /* YOUR CODE HERE */
  }
}
