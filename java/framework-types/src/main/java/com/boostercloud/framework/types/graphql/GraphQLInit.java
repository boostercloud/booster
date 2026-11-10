package com.boostercloud.framework.types.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code { type: 'connection_init', payload: { Authorization?: string } }}. Port of {@code GraphQLInit}.
 *
 * @param payload the connection parameters
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record GraphQLInit(Payload payload) implements GraphQLClientMessage {

  /** Canonical constructor; a missing payload becomes an empty one. */
  public GraphQLInit {
    payload = payload == null ? new Payload(null) : payload;
  }

  @Override
  @JsonProperty("type")
  public MessageTypes type() {
    return MessageTypes.GQL_CONNECTION_INIT;
  }

  /**
   * Connection parameters.
   *
   * @param authorization the {@code Authorization} value ({@code Bearer <jwt>}), optional
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Payload(@JsonProperty("Authorization") String authorization) {}
}
