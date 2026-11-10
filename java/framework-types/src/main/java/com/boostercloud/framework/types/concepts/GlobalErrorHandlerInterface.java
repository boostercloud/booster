package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code GlobalErrorHandlerInterface}. A global error handler is a class whose hooks are <b>optional static
 * methods</b>; core looks them up by name (so "not declared" and "returned {@code null}" stay distinguishable, as in
 * TypeScript). Each hook returns the error to propagate, or {@code null} to swallow it. Implementing this marker is
 * optional. The hooks and their parameters, in order:
 *
 * <pre>{@code
 * static Throwable onCommandHandlerError(Throwable error, CommandEnvelope commandEnvelope, CommandMetadata commandMetadata)
 * static Throwable onQueryHandlerError(Throwable error, QueryEnvelope query)
 * static Throwable onScheduledCommandHandlerError(Throwable error, ScheduledCommandEnvelope envelope, ScheduledCommandMetadata metadata)
 * static Throwable onDispatchEventHandlerError(Throwable error, Object eventEnvelope, Object eventHandlerMetadata, Object eventInstance)
 * static Throwable onReducerError(Throwable error, EventEnvelope eventEnvelope, ReducerMetadata reducerMetadata, Object eventInstance, Object snapshotInstance)
 * static Throwable onProjectionError(Throwable error, EntitySnapshotEnvelope entityEnvelope, ProjectionMetadata projectionMetadata, Object entity, Object readModel)
 * static Throwable onSnapshotPersistError(Throwable error, NonPersistedEntitySnapshotEnvelope snapshot)
 * static Throwable onEventError(Throwable error, EventEnvelope eventEnvelope)
 * static Throwable onError(Throwable error)
 * }</pre>
 *
 * <p>{@code onReducerError} cannot swallow the error in TypeScript (it is typed to return an {@code Error}).
 */
public interface GlobalErrorHandlerInterface {

  /** The hooks, with their static method names. */
  enum Hook {
    /** {@code onCommandHandlerError}. */
    ON_COMMAND_HANDLER_ERROR("onCommandHandlerError"),
    /** {@code onQueryHandlerError}. */
    ON_QUERY_HANDLER_ERROR("onQueryHandlerError"),
    /** {@code onScheduledCommandHandlerError}. */
    ON_SCHEDULED_COMMAND_HANDLER_ERROR("onScheduledCommandHandlerError"),
    /** {@code onDispatchEventHandlerError}. */
    ON_DISPATCH_EVENT_HANDLER_ERROR("onDispatchEventHandlerError"),
    /** {@code onReducerError}. */
    ON_REDUCER_ERROR("onReducerError"),
    /** {@code onProjectionError}. */
    ON_PROJECTION_ERROR("onProjectionError"),
    /** {@code onSnapshotPersistError} (deprecated, EOL v3). */
    ON_SNAPSHOT_PERSIST_ERROR("onSnapshotPersistError"),
    /** {@code onEventError}. */
    ON_EVENT_ERROR("onEventError"),
    /** {@code onError}: applied after the specific hook. */
    ON_ERROR("onError");

    private final String methodName;

    Hook(String methodName) {
      this.methodName = methodName;
    }

    /**
     * @return the static method name
     */
    public String methodName() {
      return methodName;
    }
  }
}
