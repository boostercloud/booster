package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * {@code { type: 'connection_error', payload: <error message> }}. Port of {@code GraphQLInitError}.
 *
 * @param payload the error message
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"payload", "type"})
public record GraphQLInitError(String payload) implements GraphQLServerMessage {
  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_CONNECTION_ERROR;
  }
}
