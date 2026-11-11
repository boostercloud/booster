package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code { type: 'connection_ack' }}. Port of {@code GraphQLInitAck}.
 */
public record GraphQLInitAck() implements GraphQLServerMessage {
  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_CONNECTION_ACK;
  }
}
