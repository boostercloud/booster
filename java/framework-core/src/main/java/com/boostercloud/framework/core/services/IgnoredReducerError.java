package com.boostercloud.framework.core.services;

/**
 * Thrown by {@link EventStore} when the global error handler returns no error ({@code null}) for a
 * {@code ReducerGlobalError}.
 *
 * <p>TypeScript executes {@code throw await globalErrorDispatcher.dispatch(...)} there, so a handler that ignores a
 * reducer error makes the event store throw {@code undefined}: reducer errors cannot be ignored, because no new entity
 * state can be produced, and the reduction of the entity stops (nothing is stored, the read models are not projected).
 * Java cannot throw {@code null}, so this exception stands for that {@code undefined}; the original reducer error is
 * its cause.
 */
public final class IgnoredReducerError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param eventTypeName the event whose reducer failed
   * @param entityTypeName the entity being reduced
   * @param entityID its id
   * @param reducerError the error thrown while reducing
   */
  public IgnoredReducerError(String eventTypeName, String entityTypeName, Object entityID, Throwable reducerError) {
    super("The global error handler ignored the error of the reducer for event " + eventTypeName + " of entity "
        + entityTypeName + " with ID " + entityID + ", but reducer errors cannot be ignored: the entity snapshot was"
        + " not updated", reducerError);
  }
}
