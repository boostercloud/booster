package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.envelope.EventEnvelope;

/**
 * Context of an error thrown by a reducer; hook
 * {@code onReducerError(error, eventEnvelope, reducerMetadata, eventInstance, snapshotInstance)} (note the different
 * argument order). Port of {@code ReducerGlobalError}; the original error is the last constructor argument.
 */
public final class ReducerGlobalError extends GlobalErrorContainer {
  private final EventEnvelope eventEnvelope;
  private final Object eventInstance;
  private final Object snapshotInstance;
  private final ReducerMetadata reducerMetadata;

  /**
   * @param eventEnvelope the event
   * @param eventInstance the event instance
   * @param snapshotInstance the current entity, or {@code null}
   * @param reducerMetadata the reducer
   * @param originalError the error that happened
   */
  public ReducerGlobalError(EventEnvelope eventEnvelope, Object eventInstance, Object snapshotInstance, ReducerMetadata reducerMetadata, Throwable originalError) {
    super(originalError);
    this.eventEnvelope = eventEnvelope;
    this.eventInstance = eventInstance;
    this.snapshotInstance = snapshotInstance;
    this.reducerMetadata = reducerMetadata;
  }

  /**
   * @return the event
   */
  public EventEnvelope eventEnvelope() {
    return eventEnvelope;
  }

  /**
   * @return the event instance
   */
  public Object eventInstance() {
    return eventInstance;
  }

  /**
   * @return the current entity, or {@code null}
   */
  public Object snapshotInstance() {
    return snapshotInstance;
  }

  /**
   * @return the reducer
   */
  public ReducerMetadata reducerMetadata() {
    return reducerMetadata;
  }
}
