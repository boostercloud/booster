package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code { type: 'connection_terminate' }}. Port of {@code GraphQLTerminate}.
 */
public record GraphQLTerminate() implements GraphQLClientMessage {
  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_CONNECTION_TERMINATE;
  }
}
