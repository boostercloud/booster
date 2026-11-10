package com.boostercloud.framework.types.errors;

/**
 * No reducer is registered for an event (HTTP 500). Port of {@code InvalidReducerError}: its code is always the class
 * name and it carries the event and snapshot instances (not serialized).
 */
public class InvalidReducerError extends BoosterError {

  private final transient Object eventInstance;
  private final transient Object snapshotInstance;

  /**
   * @param message the error message
   * @param eventInstance the event being reduced
   * @param snapshotInstance the current entity snapshot, or {@code null}
   */
  public InvalidReducerError(String message, Object eventInstance, Object snapshotInstance) {
    super(message);
    this.eventInstance = eventInstance;
    this.snapshotInstance = snapshotInstance;
  }

  /**
   * @return the event being reduced
   */
  public Object eventInstance() {
    return eventInstance;
  }

  /**
   * @return the current entity snapshot, or {@code null}
   */
  public Object snapshotInstance() {
    return snapshotInstance;
  }
}
