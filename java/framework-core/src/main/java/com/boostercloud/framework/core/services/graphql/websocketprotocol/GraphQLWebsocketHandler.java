package com.boostercloud.framework.core.services.graphql.websocketprotocol;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.BoosterTokenVerifier;
import com.boostercloud.framework.core.internal.Expirations;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.services.graphql.GraphQLExecution;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.envelope.GraphQLRequestValue;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.graphql.GraphQLComplete;
import com.boostercloud.framework.types.graphql.GraphQLData;
import com.boostercloud.framework.types.graphql.GraphQLErrorMessage;
import com.boostercloud.framework.types.graphql.GraphQLInit;
import com.boostercloud.framework.types.graphql.GraphQLInitAck;
import com.boostercloud.framework.types.graphql.GraphQLInitError;
import com.boostercloud.framework.types.graphql.GraphQLStart;
import com.boostercloud.framework.types.graphql.GraphQLStop;
import com.boostercloud.framework.types.graphql.GraphQLTerminate;
import com.boostercloud.framework.types.provider.ProviderConnectionsLibrary;
import graphql.ExecutionResult;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;

/**
 * Port of {@code services/graphql/websocket-protocol/graphql-websocket-protocol.ts}: the server side of the legacy
 * Apollo {@code subscriptions-transport-ws} protocol (sub-protocol {@code graphql-ws}; there is no keep-alive). The
 * provider hands over one parsed client message per {@code MESSAGE} envelope; answers go through
 * {@code connectionManager.sendMessage}.
 *
 * <ul>
 *   <li>{@code connection_init}: verifies {@code payload.Authorization} when present, stores the connection data
 *       ({@code expirationTime} = now in epoch seconds + {@code config.subscriptions.maxConnectionDurationInSeconds},
 *       the user) and sends {@code connection_ack};</li>
 *   <li>{@code start}: runs the operation with the user stored for the connection and the client's operation id;
 *       queries and mutations answer {@code data} then {@code complete}, subscriptions answer nothing (they are
 *       stored); a payload without {@code query} answers an {@code error} message. Java addition: once the stored
 *       {@code expirationTime} or the {@code exp} claim of the stored user's token has passed, the connection data and
 *       subscriptions are deleted and the operation is refused with a {@code connection_error};</li>
 *   <li>{@code stop}: stops the subscription and sends {@code complete} (a failure to send it is only logged);</li>
 *   <li>{@code connection_terminate}: deletes the connection data and its subscriptions; nothing is sent.</li>
 * </ul>
 *
 * <p>Any failure ({@code error} envelope, empty body, token verification, missing ids, unknown messages, failing
 * callbacks) is answered with {@code { type: 'connection_error', payload: <message> }}; {@link #handle} itself only
 * fails when that last message cannot be sent.
 */
public class GraphQLWebsocketHandler {

  private final BoosterConfig config;
  private final ProviderConnectionsLibrary connectionManager;
  private final GraphQLWebsocketHandlerCallbacks callbacks;
  private final BoosterTokenVerifier boosterTokenVerifier;

  /**
   * @param config the configuration
   * @param connectionManager the provider's connections library
   * @param callbacks the dispatcher callbacks
   * @param boosterTokenVerifier verifies the {@code connection_init} token
   */
  public GraphQLWebsocketHandler(BoosterConfig config, ProviderConnectionsLibrary connectionManager,
      GraphQLWebsocketHandlerCallbacks callbacks, BoosterTokenVerifier boosterTokenVerifier) {
    this.config = config;
    this.connectionManager = connectionManager;
    this.callbacks = callbacks;
    this.boosterTokenVerifier = boosterTokenVerifier;
  }

  /**
   * {@code handle(envelope)}.
   *
   * @param envelope the websocket message envelope, or the error the provider produced
   */
  public void handle(GraphQLRequestEnvelopeOrError envelope) {
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#handle");
    if (!Js.isTruthy(envelope.connectionID())) {
      // Impossible case, but just to be sure. The only thing we can do here is to log, as we don't have the
      // connection to send the message to
      logger.error("Missing websocket connectionID");
      return;
    }
    String connectionID = envelope.connectionID();
    try {
      logger.debug("Handling websocket message");
      if (envelope instanceof GraphQLRequestEnvelopeError envelopeError) {
        throw envelopeError.error();
      }
      GraphQLRequestEnvelope requestEnvelope = (GraphQLRequestEnvelope) envelope;
      GraphQLRequestValue clientMessage = requestEnvelope.value();
      if (clientMessage == null) {
        throw new IllegalStateException("Received an empty GraphQL body");
      }
      logger.debug("Received client message: ", clientMessage);
      switch (clientMessage) {
        case GraphQLInit init -> handleInit(connectionID, init);
        case GraphQLStart start -> handleStart(connectionID, requestEnvelope, start);
        case GraphQLStop stop -> handleStop(connectionID, stop);
        case GraphQLTerminate ignored -> handleTerminate(connectionID);
        // This branch should be impossible, but just in case
        default -> throw new IllegalStateException("Unknown message type. Message=[object Object]");
      }
    } catch (Throwable e) {
      logger.error(e);
      connectionManager.sendMessage(config, connectionID, new GraphQLInitError(e.getMessage()));
    }
  }

  private void handleInit(String connectionID, GraphQLInit clientMessage) {
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#handleInit");
    UserEnvelope userEnvelope = null;
    String authorization = clientMessage.payload() == null ? null : clientMessage.payload().authorization();
    if (Js.isTruthy(authorization)) {
      userEnvelope = boosterTokenVerifier.verify(authorization);
    }
    long nowEpoch = Dates.nowEpochSeconds();
    ConnectionDataEnvelope connectionData = new ConnectionDataEnvelope(
        nowEpoch + config.subscriptions.maxConnectionDurationInSeconds, userEnvelope);
    logger.debug("Storing connection data: ", connectionData);
    connectionManager.storeData(config, connectionID, connectionData);
    logger.debug("Sending ACK");
    connectionManager.sendMessage(config, connectionID, new GraphQLInitAck());
  }

  private void handleStart(String connectionID, GraphQLRequestEnvelope envelope, GraphQLStart message) {
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#handleStart");
    if (!Js.isTruthy(message.id())) {
      throw new IllegalStateException("Missing \"id\" in " + message.type() + " message");
    }
    if (message.payload() == null || !Js.isTruthy(message.payload().query())) {
      connectionManager.sendMessage(config, connectionID, GraphQLErrorMessage.of(message.id(),
          List.of(new IllegalStateException(
              "Message payload is invalid it must contain at least the \"query\" property"))));
      return;
    }
    GraphQLRequestEnvelope augmentedEnvelope = augmentEnvelope(connectionID, envelope, message);

    logger.debug("Executing operation. Envelope: ", augmentedEnvelope);
    Object result = callbacks.onStartOperation(augmentedEnvelope);

    if (isSubscription(result)) {
      logger.debug("Subscription finished.");
      return; // It was a subscription. We don't need to send any data
    }

    logger.debug("Operation finished. Sending DATA:", result);
    // It was a query or mutation. We send data and complete the operation
    connectionManager.sendMessage(config, connectionID, new GraphQLData(message.id(), toPayload(result)));
    connectionManager.sendMessage(config, connectionID, new GraphQLComplete(message.id()));
  }

  private GraphQLRequestEnvelope augmentEnvelope(String connectionID, GraphQLRequestEnvelope envelope,
      GraphQLStart message) {
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#augmentEnvelope");
    Optional<ConnectionDataEnvelope> connectionData = connectionManager.fetchData(config, connectionID);
    ConnectionDataEnvelope data = connectionData == null ? null : connectionData.orElse(null);
    logger.debug("Found connection data: ", data);
    if (data != null) {
      refuseExpired(connectionID, data);
    }
    GraphQLStart.Payload payload = message.payload();
    return envelope.toBuilder()
        .currentUser(data == null ? null : data.user())
        .value(new GraphQLOperation(payload.query(), message.id(), payload.operationName(), payload.variables()))
        .build();
  }

  /**
   * Java addition: the stored identity of a connection ends with the connection's {@code expirationTime} or with the
   * expiry of its token. TypeScript reuses it for as long as the socket stays open, which only the AWS provider bounded
   * (sockets closed after two hours, records deleted by TTL). An expired connection loses its data and subscriptions
   * ({@code onTerminate}) and the client is told to send {@code connection_init} again.
   */
  private void refuseExpired(String connectionID, ConnectionDataEnvelope data) {
    String reason;
    if (Expirations.hasPassed(data.expirationTime())) {
      reason = "The connection has expired: send connection_init to authenticate it again";
    } else if (Expirations.tokenHasExpired(data.user())) {
      reason = "The token of the connection has expired: send connection_init with a new token";
    } else {
      return;
    }
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#augmentEnvelope");
    logger.debug("Deleting the expired connection " + connectionID + " and its subscriptions");
    try {
      callbacks.onTerminate(connectionID);
    } catch (RuntimeException e) {
      logger.warn("Could not delete the data and subscriptions of the expired connection " + connectionID + ": ", e);
    }
    throw new NotAuthorizedError(reason);
  }

  private void handleStop(String connectionID, GraphQLStop message) {
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#handleStop");
    if (!Js.isTruthy(message.id())) {
      throw new IllegalStateException("Missing \"id\" in " + message.type() + " message");
    }

    logger.debug("Executing stop operation");
    callbacks.onStopOperation(connectionID, message.id());
    logger.debug("Stop operation finished");
    try {
      connectionManager.sendMessage(config, connectionID, new GraphQLComplete(message.id()));
    } catch (RuntimeException e) {
      // It could be the case that the client already closed the connection without waiting for stop operation to
      // finish. Log this but ignore it
      logger.info("Received an exception while sending the \"complete\" message after stopping the GraphQL "
          + "operation with id " + message.id() + ".The client probably closed the connection. Ignoring. ", e);
    }
  }

  private void handleTerminate(String connectionID) {
    Logger logger = Loggers.getLogger(config, "GraphQLWebsocketHandler#handleTerminate");
    logger.debug("Executing terminate operation");
    callbacks.onTerminate(connectionID);
    logger.debug("Terminate operation finished");
  }

  /** TypeScript {@code 'next' in result}: the result of a subscription is an (async) iterator. */
  private static boolean isSubscription(Object result) {
    return result instanceof Flow.Publisher<?> || result instanceof org.reactivestreams.Publisher<?>
        || result instanceof Iterator<?>;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toPayload(Object result) {
    return switch (result) {
      case null -> null;
      case Map<?, ?> map -> (Map<String, Object>) map;
      case ExecutionResult executionResult -> GraphQLExecution.toResultMap(executionResult);
      default -> BoosterJson.toRawMap(result);
    };
  }
}
