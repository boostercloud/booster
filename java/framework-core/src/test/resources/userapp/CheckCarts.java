package com.example.store.scheduledcommands;

import com.boostercloud.framework.core.decorators.ScheduledCommand;
import com.boostercloud.framework.types.concepts.Register;

@ScheduledCommand(minute = "0/5")
public class CheckCarts {
  public static void handle(Register register) {}
}
