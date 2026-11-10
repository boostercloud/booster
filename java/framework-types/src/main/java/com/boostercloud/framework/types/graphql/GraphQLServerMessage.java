package com.boostercloud.framework.types.graphql;

/**
 * A message sent by Booster over the websocket. Port of the union
 * {@code GraphQLInitError | GraphQLInitAck | GraphQLData | GraphQLError | GraphQLComplete}.
 */
public sealed interface GraphQLServerMessage
    permits GraphQLInitError, GraphQLInitAck, GraphQLData, GraphQLErrorMessage, GraphQLComplete {
  /**
   * @return the message type
   */
  MessageTypes type();
}
