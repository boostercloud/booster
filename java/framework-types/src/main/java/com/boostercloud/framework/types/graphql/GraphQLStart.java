package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * {@code { type: 'start', id, payload: { query, operationName?, variables? } }}. Port of {@code GraphQLStart}
 * (payload is {@code Omit<GraphQLOperation, 'id'>}).
 *
 * @param id the operation id
 * @param payload the operation
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"type", "id", "payload"})
public record GraphQLStart(String id, Payload payload) implements GraphQLClientMessage {

  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_START;
  }

  /**
   * The operation to start.
   *
   * @param query the GraphQL document
   * @param operationName the operation name, optional
   * @param variables the variables, optional
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Payload(String query, String operationName, Map<String, Object> variables) {}
}
