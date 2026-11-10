package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * {@code { type: 'complete', id }}. Port of {@code GraphQLComplete}.
 *
 * @param id the operation id
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"id", "type"})
public record GraphQLComplete(String id) implements GraphQLServerMessage {
  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_COMPLETE;
  }
}
