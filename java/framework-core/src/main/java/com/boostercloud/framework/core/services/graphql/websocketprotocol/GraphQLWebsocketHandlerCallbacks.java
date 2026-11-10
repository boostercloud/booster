package com.boostercloud.framework.core.services.graphql.websocketprotocol;

import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;

/**
 * Port of {@code GraphQLWebsocketHandlerCallbacks}: what the websocket protocol handler asks the GraphQL dispatcher to
 * do.
 *
 * <p>Owned by stage <b>core-3</b> (spec 08 §2.5).
 */
public interface GraphQLWebsocketHandlerCallbacks {
  /**
   * @param envelope the {@code start} message as a request envelope
   * @return the execution result, or a stream of results for subscriptions
   */
  Object onStartOperation(GraphQLRequestEnvelope envelope);

  /**
   * @param connectionID the connection
   * @param messageID the operation id
   */
  void onStopOperation(String connectionID, String messageID);

  /**
   * @param connectionID the connection
   */
  void onTerminate(String connectionID);
}
