package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerInterface.Hook;
import com.boostercloud.framework.types.errors.CommandHandlerGlobalError;
import com.boostercloud.framework.types.errors.EventGlobalError;
import com.boostercloud.framework.types.errors.EventHandlerGlobalError;
import com.boostercloud.framework.types.errors.GlobalErrorContainer;
import com.boostercloud.framework.types.errors.ProjectionGlobalError;
import com.boostercloud.framework.types.errors.QueryHandlerGlobalError;
import com.boostercloud.framework.types.errors.ReducerGlobalError;
import com.boostercloud.framework.types.errors.ScheduleCommandGlobalError;
import com.boostercloud.framework.types.errors.SnapshotPersistHandlerGlobalError;
import java.util.Optional;

/**
 * Port of {@code booster-global-error-dispatcher.ts}: routes a {@link GlobalErrorContainer} to the application's
 * {@code @GlobalErrorHandler} class and returns the error the caller must throw, or {@code null} to swallow it. Every
 * caller does {@code Throwable error = dispatcher.dispatch(container); if (error != null) throw error}.
 *
 * <p>The hooks are optional static methods looked up by name (see
 * {@link com.boostercloud.framework.types.concepts.GlobalErrorHandlerInterface}). The outcome is exactly the
 * TypeScript one: the specific hook of the container's exact class runs first ({@code onCommandHandlerError},
 * {@code onQueryHandlerError}, ...; none for a bare container), then {@code onError} with its result (skipped when it
 * is {@code null}). A missing specific hook, a missing {@code onError} for a non-null error, or a hook that throws
 * ends the dispatch: the error (the original one, or the thrown one) is logged as
 * {@code Unhandled error inside the global error handler. When handling error <original>} and returned.
 */
public class BoosterGlobalErrorDispatcher {

  private final BoosterConfig config;
  private final Class<?> errorHandler;

  /**
   * Captures {@code config.globalErrorsHandler} at construction time, as TypeScript does.
   *
   * @param config the configuration
   */
  public BoosterGlobalErrorDispatcher(BoosterConfig config) {
    this.config = config;
    this.errorHandler = config.globalErrorsHandler == null ? null : config.globalErrorsHandler.type();
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * @return the global error handler class, or {@code null}
   */
  public Class<?> errorHandler() {
    return errorHandler;
  }

  /**
   * {@code dispatch(error)}.
   *
   * @param error the container
   * @return the error to throw, or {@code null} when the handler swallowed it
   */
  public Throwable dispatch(GlobalErrorContainer error) {
    Logger logger = Loggers.getLogger(config, "BoosterGlobalErrorDispatcher#dispatch");
    if (errorHandler == null) {
      return error.originalError();
    }
    Throwable newError;
    try {
      newError = switch (error) {
        case CommandHandlerGlobalError e ->
            callHook(Hook.ON_COMMAND_HANDLER_ERROR, e, e.originalError(), e.commandEnvelope(), e.commandMetadata());
        case QueryHandlerGlobalError e -> callHook(Hook.ON_QUERY_HANDLER_ERROR, e, e.originalError(), e.query());
        case ScheduleCommandGlobalError e -> callHook(Hook.ON_SCHEDULED_COMMAND_HANDLER_ERROR, e, e.originalError(),
            e.scheduleCommandEnvelope(), e.scheduleCommandMetadata());
        case EventHandlerGlobalError e -> callHook(Hook.ON_DISPATCH_EVENT_HANDLER_ERROR, e, e.originalError(),
            e.eventEnvelope(), e.eventHandlerMetadata(), e.eventInstance());
        case ReducerGlobalError e -> callHook(Hook.ON_REDUCER_ERROR, e, e.originalError(), e.eventEnvelope(),
            e.reducerMetadata(), e.eventInstance(), e.snapshotInstance());
        case ProjectionGlobalError e -> callHook(Hook.ON_PROJECTION_ERROR, e, e.originalError(), e.entityEnvelope(),
            e.projectionMetadata(), e.entity(), e.readModel());
        case SnapshotPersistHandlerGlobalError e ->
            callHook(Hook.ON_SNAPSHOT_PERSIST_ERROR, e, e.originalError(), e.snapshot());
        case EventGlobalError e -> callHook(Hook.ON_EVENT_ERROR, e, e.originalError(), e.eventEnvelope());
        default -> error.originalError();
      };
      newError = handleGenericError(newError);
    } catch (Throwable e) {
      logger.error("Unhandled error inside the global error handler. When handling error "
          + Js.errorToString(error.originalError()), e);
      return e;
    }
    return newError;
  }

  /** A hook that is declared but cannot take the arguments fails with an explanation (logged and returned). */
  private Throwable callHook(Hook hook, GlobalErrorContainer error, Object... args) {
    if (!UserCode.hasStaticMethod(errorHandler, hook.methodName())) {
      throw UserCode.rethrow(error.originalError());
    }
    return asError(UserCode.invokeStatic(errorHandler, hook.methodName(), args));
  }

  private Throwable handleGenericError(Throwable error) {
    if (error == null) {
      return null;
    }
    if (!UserCode.hasStaticMethod(errorHandler, Hook.ON_ERROR.methodName())) {
      throw UserCode.rethrow(error);
    }
    return asError(UserCode.invokeStatic(errorHandler, Hook.ON_ERROR.methodName(), error));
  }

  private static Throwable asError(Object result) {
    return switch (result) {
      case null -> null;
      case Throwable throwable -> throwable;
      case Optional<?> optional -> (Throwable) optional.orElse(null);
      default -> throw new IllegalStateException(
          "Global error handler hooks must return a Throwable (or null to ignore the error), but one returned "
              + result.getClass().getName());
    };
  }
}
