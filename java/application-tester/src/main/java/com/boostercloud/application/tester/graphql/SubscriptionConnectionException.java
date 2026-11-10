package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.BoosterJson;

/**
 * The websocket connection of a client with subscriptions was not acknowledged. Thrown by
 * {@code GraphQLHelper.clientWithSubscriptions} when the server answers {@code connection_init} with
 * {@code connection_error} (the TypeScript promise rejects with the message payload, which {@link #payload()}
 * returns; the exception message is the payload itself when it is a string, its JSON otherwise), and when no
 * {@code connection_ack} arrives within the connection timeout (Java only).
 */
public final class SubscriptionConnectionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The {@code connection_error} payload, {@code null} for timeouts. */
  private final transient Object payload;

  private SubscriptionConnectionException(String message, Object payload, Throwable cause) {
    super(message, cause);
    this.payload = payload;
  }

  /**
   * @param payload the payload of the {@code connection_error} message
   * @return the exception
   */
  public static SubscriptionConnectionException connectionError(Object payload) {
    String message = payload instanceof String text ? text : BoosterJson.toJson(payload);
    return new SubscriptionConnectionException(message, payload, null);
  }

  /**
   * @param message the explanation
   * @return the exception for a connection that was never acknowledged
   */
  public static SubscriptionConnectionException timeout(String message) {
    return new SubscriptionConnectionException(message, null, null);
  }

  /**
   * @return the payload of the {@code connection_error} message (Booster sends the error message), or {@code null}
   */
  public Object payload() {
    return payload;
  }
}
