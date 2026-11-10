package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Message types of the legacy Apollo {@code subscriptions-transport-ws} protocol (websocket sub-protocol
 * {@code graphql-ws}). Port of {@code MessageTypes}; {@link #value()} is the wire value of the {@code type} field.
 * Keep-alive ({@code ka}) messages are not used.
 */
public enum MessageTypes {
  /** {@code connection_init} (client to server). */
  GQL_CONNECTION_INIT("connection_init"),
  /** {@code connection_ack} (server to client). */
  GQL_CONNECTION_ACK("connection_ack"),
  /** {@code connection_error} (server to client). */
  GQL_CONNECTION_ERROR("connection_error"),
  /** {@code connection_terminate} (client to server). */
  GQL_CONNECTION_TERMINATE("connection_terminate"),
  /** {@code start} (client to server). */
  GQL_START("start"),
  /** {@code data} (server to client). */
  GQL_DATA("data"),
  /** {@code error} (server to client). */
  GQL_ERROR("error"),
  /** {@code complete} (server to client). */
  GQL_COMPLETE("complete"),
  /** {@code stop} (client to server). */
  GQL_STOP("stop");

  private final String value;

  MessageTypes(String value) {
    this.value = value;
  }

  /**
   * @return the wire value
   */
  @JsonValue
  public String value() {
    return value;
  }

  /**
   * @param value a wire value
   * @return the message type
   * @throws IllegalArgumentException for unknown values
   */
  @JsonCreator
  public static MessageTypes fromValue(String value) {
    for (MessageTypes type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown message type '" + value + "'");
  }

  @Override
  public String toString() {
    return value;
  }
}
