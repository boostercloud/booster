package com.boostercloud.framework.core.integration.app.handlers;

import com.boostercloud.framework.core.decorators.GlobalErrorHandler;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.errors.BoosterError;
import java.util.Map;

/** The application's global error handler. */
@GlobalErrorHandler
public class StoreErrorHandler {
  /**
   * Swallows {@code ignore me}, turns other plain failures into a coded Booster error.
   *
   * @param error the failure
   * @param command the command
   * @return the error to report, or {@code null}
   */
  public static Throwable onCommandHandlerError(Throwable error, CommandEnvelope command) {
    if ("ignore me".equals(error.getMessage())) {
      return null;
    }
    if (error instanceof IllegalStateException) {
      return new BoosterError("Command " + command.typeName() + " failed: " + error.getMessage(), "COMMAND_FAILED",
          Map.of("reason", error.getMessage()));
    }
    return error;
  }

  /**
   * @param error the failure of an event handler
   * @param eventEnvelope the event
   * @return the same error
   */
  public static Throwable onDispatchEventHandlerError(Throwable error, Object eventEnvelope) {
    AppState.EVENT_HANDLER_ERRORS.add(error.getMessage());
    return error;
  }

  /**
   * @param error any reported failure
   * @return the same error
   */
  public static Throwable onError(Throwable error) {
    AppState.ERRORS.add(error.getMessage());
    return error;
  }
}
