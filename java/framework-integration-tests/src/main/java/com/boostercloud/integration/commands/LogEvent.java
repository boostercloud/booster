package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.LogEventReceived;

/**
 * Starts the chain {@code LogEventReceived} → {@code GlobalHandler} → {@code LogEventReceivedTest} →
 * {@code LogEventReceivedTestReadModel} ({@code src/commands/log-event.ts}). Its simple name is the one of the entity
 * {@code entities.LogEvent}, as in TypeScript (commands and entities have separate registries).
 *
 * @param logEventId the entity
 * @param value the value to propagate
 */
@Command(authorize = All.class)
public record LogEvent(UUID logEventId, String value) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(LogEvent command, Register register) {
    register.events(new LogEventReceived(command.logEventId(), command.value()));
  }
}
