package com.boostercloud.framework.provider.azure.eventhubs;

/** A failed Event Hubs request. */
public class EventHubsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int statusCode;
  private final boolean transientFailure;

  /**
   * @param message what went wrong
   * @param statusCode the HTTP status, or 0 for connection failures
   * @param transientFailure whether the failure is retryable
   * @param cause the cause, or {@code null}
   */
  public EventHubsException(String message, int statusCode, boolean transientFailure, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
    this.transientFailure = transientFailure;
  }

  /**
   * @return the HTTP status, or 0
   */
  public int statusCode() {
    return statusCode;
  }

  /**
   * @return whether the failure is retryable
   */
  public boolean isTransient() {
    return transientFailure;
  }
}
