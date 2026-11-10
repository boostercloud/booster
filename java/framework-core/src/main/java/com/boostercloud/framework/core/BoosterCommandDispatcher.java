package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.services.FilterHelpers;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.CommandInterface;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.errors.CommandHandlerGlobalError;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code booster-command-dispatcher.ts}: runs one command. Traced as {@code COMMAND_HANDLER}.
 *
 * <ol>
 *   <li>{@code version} must be set (non-zero): {@link InvalidParameterError}
 *       {@code The required command "version" was not present};</li>
 *   <li>the command must be registered: {@link NotFoundError} {@code Could not find a proper handler for <type>};</li>
 *   <li>the authorizer runs with the un-migrated envelope;</li>
 *   <li>the envelope is migrated ({@link SchemaMigrator});</li>
 *   <li>a {@link Register} is created with the context's response headers (shared by reference);</li>
 *   <li>inside a {@code try}: the {@code before} hooks run in order on the raw input, the command instance is
 *       created, and the static {@code handle(command, register)} runs; any failure goes to the global error handler,
 *       which rethrows it (or swallows it, in which case execution continues and the events registered so far are
 *       stored);</li>
 *   <li>the registered events are stored ({@link RegisterHandler#handle}); failures here are not routed to the global
 *       error handler;</li>
 *   <li>the handler's result is returned ({@code null} for {@code void}).</li>
 * </ol>
 */
public class BoosterCommandDispatcher {

  private final BoosterConfig config;
  private final BoosterGlobalErrorDispatcher globalErrorDispatcher;

  /**
   * @param config the configuration (the global error handler is captured now)
   */
  public BoosterCommandDispatcher(BoosterConfig config) {
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
   * {@code dispatchCommand(commandEnvelope, context)}.
   *
   * @param commandEnvelope the command
   * @param context the GraphQL resolver context (its response headers are shared with the register)
   * @return the handler's result
   */
  public Object dispatchCommand(CommandEnvelope commandEnvelope, GraphQLResolverContext context) {
    return Tracer.trace(TraceActionTypes.COMMAND_HANDLER.value(), BoosterCommandDispatcher.class, "dispatchCommand",
        Arrays.asList(commandEnvelope, context), () -> doDispatchCommand(commandEnvelope, context));
  }

  private Object doDispatchCommand(CommandEnvelope commandEnvelope, GraphQLResolverContext context) {
    Logger logger = Loggers.getLogger(config, "BoosterCommandDispatcher#dispatchCommand");
    logger.debug("Dispatching the following command envelope: ", commandEnvelope);
    if (commandEnvelope.version() == 0) {
      throw new InvalidParameterError("The required command \"version\" was not present");
    }
    CommandMetadata commandMetadata = config.commandHandlers.get(commandEnvelope.typeName());
    if (commandMetadata == null) {
      throw new NotFoundError("Could not find a proper handler for " + commandEnvelope.typeName());
    }
    commandMetadata.authorizer().authorize(commandEnvelope.currentUser(), commandEnvelope);

    Class<?> commandClass = commandMetadata.type();
    logger.debug("Found the following command:", commandClass.getSimpleName());

    CommandEnvelope migratedCommandEnvelope = new SchemaMigrator(config).migrate(commandEnvelope);
    Object result = null;
    Register register = new Register(migratedCommandEnvelope.requestID(), responseHeaders(context),
        RegisterHandler::flush, migratedCommandEnvelope.currentUser(), migratedCommandEnvelope.context());
    try {
      Map<String, Object> commandInput = FilterHelpers.applyBeforeFunctions(migratedCommandEnvelope.value(),
          commandMetadata.before(), migratedCommandEnvelope.currentUser());
      Object commandInstance = Instances.createInstance(commandClass, commandInput);
      logger.debug("Calling \"handle\" method on command: ", commandClass);
      result = UserCode.invokeStatic(commandClass, CommandInterface.HANDLE_METHOD, commandInstance, register);
    } catch (Exception e) {
      Throwable error = globalErrorDispatcher.dispatch(new CommandHandlerGlobalError(migratedCommandEnvelope, commandMetadata, e));
      if (error != null) {
        throw UserCode.rethrow(error);
      }
    }
    logger.debug("Command dispatched with register: ", register);
    RegisterHandler.handle(config, register);
    return result;
  }

  static Map<String, String> responseHeaders(GraphQLResolverContext context) {
    if (context == null || context.responseHeaders() == null) {
      return new LinkedHashMap<>();
    }
    return context.responseHeaders();
  }
}
