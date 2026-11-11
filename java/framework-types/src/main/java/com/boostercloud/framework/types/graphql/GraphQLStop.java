package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code { type: 'stop', id }}. Port of {@code GraphQLStop}.
 *
 * @param id the operation id to stop
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record GraphQLStop(String id) implements GraphQLClientMessage {
  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_STOP;
  }
}
