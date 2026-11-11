package com.boostercloud.framework.core.integration.app.handlers;

import com.boostercloud.framework.core.decorators.GlobalEventHandler;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.types.concepts.Register;

/** Receives every event and notification. */
@GlobalEventHandler
public class RecordAllEvents {
  /**
   * @param event the event instance
   * @param register the register
   */
  public static void handle(Object event, Register register) {
    AppState.EVENTS.add(event.getClass().getSimpleName());
  }
}
