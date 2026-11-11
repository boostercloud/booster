package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.ScheduledCommandInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.errors.ScheduleCommandGlobalError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Port of {@code booster-scheduled-command-dispatcher.ts}: the entry point of scheduler triggers. No version check,
 * no authorization, no schema migration and no hooks: the static {@code handle(register)} of the scheduled command
 * runs with a register that has empty response headers and no user; failures go to the global error handler
 * (events registered before a swallowed failure are still stored).
 */
public class BoosterScheduledCommandDispatcher {

  private final BoosterConfig config;
  private final BoosterGlobalErrorDispatcher globalErrorDispatcher;

  /**
   * @param config the configuration (the global error handler is captured now)
   */
  public BoosterScheduledCommandDispatcher(BoosterConfig config) {
    this.config = config;
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config);
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatchCommand(commandEnvelope)}, traced as {@code SCHEDULED_COMMAND_HANDLER}.
   *
   * @param commandEnvelope the scheduled command
   * @throws NotFoundError {@code Could not find a proper handler for <type>}
   */
  public void dispatchCommand(ScheduledCommandEnvelope commandEnvelope) {
    Tracer.trace(TraceActionTypes.SCHEDULED_COMMAND_HANDLER.value(), BoosterScheduledCommandDispatcher.class,
        "dispatchCommand", Arrays.asList(commandEnvelope), () -> {
          doDispatchCommand(commandEnvelope);
          return null;
        });
  }

  private void doDispatchCommand(ScheduledCommandEnvelope commandEnvelope) {
    Logger logger = Loggers.getLogger(config, "BoosterScheduledCommandDispatcher#dispatchCommand");
    logger.debug("Dispatching the following scheduled command envelope: ", commandEnvelope);
    ScheduledCommandMetadata commandMetadata = config.scheduledCommandHandlers.get(commandEnvelope.typeName());
    if (commandMetadata == null) {
      throw new NotFoundError("Could not find a proper handler for " + commandEnvelope.typeName());
    }
    Class<?> commandClass = commandMetadata.type();
    logger.debug("Found the following command:", commandClass.getSimpleName());
    Register register =
        new Register(commandEnvelope.requestID(), new LinkedHashMap<>(), RegisterHandler::flush, null, commandEnvelope.context());
    try {
      logger.debug("Calling \"handle\" method on command: ", commandClass);
      UserCode.invokeStatic(commandClass, ScheduledCommandInterface.HANDLE_METHOD, register);
    } catch (Exception e) {
      Throwable error = globalErrorDispatcher.dispatch(new ScheduleCommandGlobalError(commandEnvelope, commandMetadata, e));
      if (error != null) {
        throw UserCode.rethrow(error);
      }
    }
    logger.debug("Command dispatched with register: ", register);
    RegisterHandler.handle(config, register);
  }

  /**
   * {@code dispatch(request)}: the provider entry point; converts the raw request with
   * {@code provider.scheduled().rawToEnvelope} and dispatches it.
   *
   * @param request the provider-specific request
   */
  public void dispatch(Object request) {
    Logger logger = Loggers.getLogger(config, "BoosterScheduledCommandDispatcher#dispatch");
    ScheduledCommandEnvelope envelopeOrError = config.provider().scheduled().rawToEnvelope(config, request);
    logger.debug("Received ScheduledCommand envelope...", envelopeOrError);
    dispatchCommand(envelopeOrError);
  }
}
