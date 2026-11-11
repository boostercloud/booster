package com.boostercloud.framework.types.graphql;

import com.boostercloud.framework.types.envelope.GraphQLRequestValue;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A message sent by the client over the websocket. Port of the union
 * {@code GraphQLInit | GraphQLStart | GraphQLStop | GraphQLTerminate}, discriminated by {@code type}. Reading a
 * message with an unknown {@code type} fails with a Jackson exception; callers that must report
 * {@code Unknown message type} should check {@link MessageTypes#fromValue(String)} first.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GraphQLInit.class, name = "connection_init"),
  @JsonSubTypes.Type(value = GraphQLStart.class, name = "start"),
  @JsonSubTypes.Type(value = GraphQLStop.class, name = "stop"),
  @JsonSubTypes.Type(value = GraphQLTerminate.class, name = "connection_terminate")
})
public sealed interface GraphQLClientMessage extends GraphQLRequestValue
    permits GraphQLInit, GraphQLStart, GraphQLStop, GraphQLTerminate {
  /**
   * @return the message type
   */
  MessageTypes type();
}
