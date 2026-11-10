package com.boostercloud.framework.core.services.graphql.websocketprotocol;

import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;

/**
 * Port of {@code GraphQLWebsocketHandlerCallbacks}: what the websocket protocol handler asks the GraphQL dispatcher to
 * do.
 */
public interface GraphQLWebsocketHandlerCallbacks {
  /**
   * {@code onStartOperation(envelope)}: runs the operation of a {@code start} message.
   *
   * @param envelope the {@code start} message as a request envelope (its value is the operation, with the message
   *     id)
   * @return the execution result as a JSON map for queries and mutations, or the publisher (a
   *     {@code java.util.concurrent.Flow.Publisher}, an {@code org.reactivestreams.Publisher} or an
   *     {@link java.util.Iterator}) of results for subscriptions — TypeScript tells them apart with
   *     {@code 'next' in result}
   */
  Object onStartOperation(GraphQLRequestEnvelope envelope);

  /**
   * {@code onStopOperation(connectionID, messageID)}: stops a subscription.
   *
   * @param connectionID the connection
   * @param messageID the operation id
   */
  void onStopOperation(String connectionID, String messageID);

  /**
   * {@code onTerminate(connectionID)}: the client closes the connection.
   *
   * @param connectionID the connection
   */
  void onTerminate(String connectionID);
}
