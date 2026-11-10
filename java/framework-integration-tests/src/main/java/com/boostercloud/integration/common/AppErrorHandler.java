package com.boostercloud.integration.common;

import com.boostercloud.framework.core.decorators.GlobalErrorHandler;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.integration.Constants;
import com.boostercloud.metadata.ClassMetadata;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * The application's global error handler ({@code src/common/app-error-handler.ts}). Each hook returns the error to
 * report, or {@code null} to swallow it; {@link #onError} then suffixes every reported message with {@code -onError}.
 * The magic ids of {@link Constants} select the branches; the TypeScript {@code console.log} calls go to the standard
 * output.
 *
 * <p>TypeScript returns plain {@code Error}s; Java returns {@link IllegalStateException}s with the same messages
 * (ARCHITECTURE §7). {@code JSON.stringify(metadata)} is {@link JsonStringify#stringify}: the Java metadata records
 * rendered with the TypeScript shapes, functions left out; the type names inside are those of the Java metadata.
 */
@GlobalErrorHandler
public final class AppErrorHandler {

  private AppErrorHandler() {}

  /**
   * @param error the failure of a command handler or of its before hooks
   * @param commandEnvelope the command
   * @param commandMetadata the command metadata
   * @return the error to report, or {@code null}
   */
  public static Throwable onCommandHandlerError(
      Throwable error, CommandEnvelope commandEnvelope, CommandMetadata commandMetadata) {
    System.out.println(commandEnvelope);
    System.out.println(commandMetadata);
    Object cartId = valueOf(commandEnvelope.value(), "cartId");
    if (Constants.COMMAND_HANDLER_ERROR_IGNORED_CART_ID.equals(cartId)) {
      return null;
    }
    if (Constants.COMMAND_HANDLER_ERROR_CART_ID.equals(cartId)) {
      return new IllegalStateException(
          message(error) + "-onCommandHandlerError with metadata: " + stringify(commandMetadata));
    }
    if (Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_ID.equals(cartId)) {
      return new IllegalStateException(
          message(error) + "-onBeforeCommandHandlerError with metadata: " + stringify(commandMetadata));
    }
    return error;
  }

  /**
   * @param error the failure of a query handler
   * @param query the query
   * @return the error to report
   */
  public static Throwable onQueryHandlerError(Throwable error, QueryEnvelope query) {
    if (Constants.QUERY_HANDLER_ERROR_CART_ID.equals(valueOf(query.value(), "cartId"))) {
      return new IllegalStateException(message(error) + "-onQueryHandlerError");
    }
    return error;
  }

  /**
   * @param error the failure of a scheduled command
   * @param scheduledCommandEnvelope the scheduled command
   * @param scheduledCommandMetadata its metadata
   * @return the error to report (always a new one)
   */
  public static Throwable onScheduledCommandHandlerError(Throwable error,
      ScheduledCommandEnvelope scheduledCommandEnvelope, ScheduledCommandMetadata scheduledCommandMetadata) {
    System.out.println("onScheduledCommandHandlerError");
    System.out.println(scheduledCommandEnvelope);
    System.out.println(scheduledCommandMetadata);
    return new IllegalStateException(message(error) + "-onScheduledCommandHandlerError with metadata: "
        + stringify(scheduledCommandMetadata));
  }

  /**
   * @param error the failure of an event handler
   * @param eventEnvelope the event (or notification)
   * @param eventHandlerMetadata the handler metadata (always {@code null} in Java and TypeScript)
   * @param eventInstance the event instance
   * @return the error to report
   */
  public static Throwable onDispatchEventHandlerError(
      Throwable error, Object eventEnvelope, Object eventHandlerMetadata, Object eventInstance) {
    System.out.println(eventEnvelope);
    System.out.println(eventHandlerMetadata);
    if (Constants.DISPATCH_EVENT_ERROR_CART_ID.equals(entityIdOf(eventInstance))) {
      return new IllegalStateException(message(error) + "-onDispatchEventHandlerError with metadata: "
          + stringify(eventHandlerMetadata));
    }
    return error;
  }

  /**
   * @param error the failure of a reducer
   * @param eventEnvelope the reduced event
   * @param reducerMetadata the reducer
   * @param eventInstance the event instance
   * @param snapshotInstance the current entity, or {@code null}
   * @return the error to report (never {@code null}, as in TypeScript)
   */
  public static Throwable onReducerError(Throwable error, EventEnvelope eventEnvelope, ReducerMetadata reducerMetadata,
      Object eventInstance, Object snapshotInstance) {
    System.out.println(eventEnvelope);
    System.out.println(reducerMetadata);
    System.out.println(snapshotInstance);
    if (Constants.REDUCER_ERROR_CART_ID.equals(entityIdOf(eventInstance))) {
      return new IllegalStateException(
          message(error) + "-onReducerError with metadata: " + stringify(reducerMetadata));
    }
    return error;
  }

  /**
   * @param error the failure of a projection
   * @param entityEnvelope the projected snapshot
   * @param projectionMetadata the projection
   * @param entity the entity instance
   * @param readModel the current read model, or {@code null}
   * @return the error to report
   */
  public static Throwable onProjectionError(Throwable error, EntitySnapshotEnvelope entityEnvelope,
      ProjectionMetadata projectionMetadata, Object entity, Object readModel) {
    System.out.println(entityEnvelope);
    System.out.println(projectionMetadata);
    System.out.println(readModel);
    if (Constants.PROJECTION_ERROR_CART_ID.equals(idOf(entity))) {
      return new IllegalStateException(
          message(error) + "-onProjectionError with metadata: " + stringify(projectionMetadata));
    }
    return error;
  }

  /**
   * @param error the failure of an event
   * @param eventEnvelope the event
   * @return the error to report, or {@code null}
   */
  public static Throwable onEventError(Throwable error, EventEnvelope eventEnvelope) {
    String entityId = eventEnvelope.entityID() == null ? null : eventEnvelope.entityID().value();
    if (Constants.EVENT_ERROR_CART_ID.equals(entityId)) {
      return new IllegalStateException(message(error) + "-onEventError");
    }
    if (Constants.IGNORE_EVENT_ERROR_CART_ID.equals(entityId)) {
      return null;
    }
    return error;
  }

  /**
   * Applied to every reported error: appends {@code -onError} to its message ({@code undefined-onError} without an
   * error, as TypeScript's {@code error?.message + '-onError'}).
   *
   * @param error the error, or {@code null}
   * @return a new error
   */
  public static Throwable onError(Throwable error) {
    return new IllegalStateException((error == null ? "undefined" : message(error)) + "-onError");
  }

  /** {@code error.message}: a JavaScript error without message has {@code ""}. */
  static String message(Throwable error) {
    return error.getMessage() == null ? "" : error.getMessage();
  }

  /** {@code JSON.stringify(value)}, see {@link JsonStringify}. */
  static String stringify(Object value) {
    return JsonStringify.stringify(value);
  }

  private static Object valueOf(Map<String, Object> value, String key) {
    Object result = value == null ? null : value.get(key);
    return result == null ? null : result.toString();
  }

  /** {@code eventInstance?.entityID ? eventInstance.entityID() : ''}. */
  static String entityIdOf(Object eventInstance) {
    if (eventInstance == null) {
      return "";
    }
    try {
      Method method = eventInstance.getClass().getMethod("entityID");
      return Objects.toString(method.invoke(eventInstance), null);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return "";
    }
  }

  /** {@code entity?.id}. */
  static String idOf(Object entity) {
    if (entity == null) {
      return null;
    }
    return ClassMetadata.of(entity.getClass()).field("id")
        .map(property -> property.readValue(entity))
        .map(Object::toString)
        .orElse(null);
  }
}
