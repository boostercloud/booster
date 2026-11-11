package com.boostercloud.integration.eventhandlers;

import com.boostercloud.framework.core.decorators.GlobalEventHandler;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.integration.events.LogEventReceived;
import com.boostercloud.integration.events.LogEventReceivedTest;

/**
 * Receives every event and notification ({@code src/event-handlers/global-handler.ts}); turns each
 * {@link LogEventReceived} into a {@link LogEventReceivedTest}.
 */
@GlobalEventHandler
public class GlobalHandler {

  /**
   * @param event the event or notification instance
   * @param register the register
   */
  public static void handle(Object event, Register register) {
    if (event instanceof LogEventReceived logEventReceived) {
      register.events(new LogEventReceivedTest(logEventReceived.entityID(), logEventReceived.value()));
    }
  }
}
