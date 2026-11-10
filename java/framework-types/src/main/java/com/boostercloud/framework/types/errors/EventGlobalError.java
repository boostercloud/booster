package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.envelope.EventEnvelope;

/**
 * Context of an error while processing an event envelope; hook {@code onEventError(error, eventEnvelope)}. Port of {@code EventGlobalError}; the original error is the last constructor argument.
 */
public final class EventGlobalError extends GlobalErrorContainer {
  private final EventEnvelope eventEnvelope;

  /**
   * @param eventEnvelope the event
   * @param originalError the error that happened
   */
  public EventGlobalError(EventEnvelope eventEnvelope, Throwable originalError) {
    super(originalError);
    this.eventEnvelope = eventEnvelope;
  }

  /**
   * @return the event
   */
  public EventEnvelope eventEnvelope() {
    return eventEnvelope;
  }
}
