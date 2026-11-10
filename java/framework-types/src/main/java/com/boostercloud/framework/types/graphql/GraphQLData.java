package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * {@code { type: 'data', id, payload: ExecutionResult }}. Port of {@code GraphQLData}; the payload is the
 * specification form of a GraphQL execution result ({@code data}, {@code errors}, {@code extensions}), e.g.
 * graphql-java's {@code ExecutionResult.toSpecification()}.
 *
 * @param id the operation id
 * @param payload the execution result
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"id", "payload", "type"})
public record GraphQLData(String id, Map<String, Object> payload) implements GraphQLServerMessage {
  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_DATA;
  }
}
