package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A stored websocket connection: {@code ConnectionDataEnvelope} plus its {@code connectionID}. Port of the
 * TypeScript {@code interface ConnectionData extends ConnectionDataEnvelope { connectionID }} (the TypeScript
 * {@code SimpleRegistryTypes} union is {@code ConnectionData | SubscriptionEnvelope}, both accepted by
 * {@link WebSocketRegistry#store(Object)}).
 *
 * @param expirationTime expiration, in epoch seconds
 * @param user the authenticated user, or {@code null}
 * @param connectionID the connection id
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"expirationTime", "user", "connectionID"})
public record ConnectionData(long expirationTime, UserEnvelope user, String connectionID) {

  /**
   * {@code { ...data, connectionID }}.
   *
   * @param data the connection data
   * @param connectionID the connection id
   * @return the stored shape
   */
  public static ConnectionData of(ConnectionDataEnvelope data, String connectionID) {
    return new ConnectionData(data.expirationTime(), data.user(), connectionID);
  }

  /**
   * @return the connection data without the id
   */
  public ConnectionDataEnvelope toEnvelope() {
    return new ConnectionDataEnvelope(expirationTime, user);
  }
}
