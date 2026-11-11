package com.boostercloud.framework.types.errors;

/**
 * Context of an error thrown by an event handler; hook
 * {@code onDispatchEventHandlerError(error, eventEnvelope, eventHandlerMetadata, eventInstance)} (note the different
 * argument order). Port of {@code EventHandlerGlobalError}; the original error is the last constructor argument.
 */
public final class EventHandlerGlobalError extends GlobalErrorContainer {
  private final Object eventEnvelope;
  private final Object eventInstance;
  private final Object eventHandlerMetadata;

  /**
   * @param eventEnvelope the event envelope (an {@code EventEnvelope} or a notification)
   * @param eventInstance the event instance
   * @param eventHandlerMetadata metadata of the failing handler, usually {@code null}
   * @param originalError the error that happened
   */
  public EventHandlerGlobalError(Object eventEnvelope, Object eventInstance, Object eventHandlerMetadata, Throwable originalError) {
    super(originalError);
    this.eventEnvelope = eventEnvelope;
    this.eventInstance = eventInstance;
    this.eventHandlerMetadata = eventHandlerMetadata;
  }

  /**
   * @return the event envelope (an {@code EventEnvelope} or a notification)
   */
  public Object eventEnvelope() {
    return eventEnvelope;
  }

  /**
   * @return the event instance
   */
  public Object eventInstance() {
    return eventInstance;
  }

  /**
   * @return metadata of the failing handler, usually {@code null}
   */
  public Object eventHandlerMetadata() {
    return eventHandlerMetadata;
  }
}
