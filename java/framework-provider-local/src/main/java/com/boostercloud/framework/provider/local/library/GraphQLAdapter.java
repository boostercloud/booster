package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.envelope.GraphQLRequestValue;
import com.boostercloud.framework.types.graphql.GraphQLClientMessage;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Turns the raw requests of the local provider into GraphQL request envelopes. Port of
 * {@code library/graphql-adapter.ts}.
 *
 * <p>An {@link ExpressWebSocketMessage} is a websocket event; anything else is an HTTP request
 * ({@link LocalHttpRequest}, or a map with {@code headers} and {@code body}).
 *
 * <ul>
 *   <li>HTTP: {@code eventType MESSAGE}, no {@code connectionID}, {@code token} = the raw {@code authorization} header
 *       (including any {@code Bearer } prefix), {@code value} = the JSON body as a {@link GraphQLOperation},
 *       {@code context = { request: { headers, body }, rawContext: request }}.
 *   <li>Websocket: {@code eventType} and {@code connectionID} from the connection context, {@code token} and headers
 *       from the upgrade request, {@code value} = the parsed message as a {@link GraphQLClientMessage}.
 * </ul>
 *
 * <p>Java-specific: the envelope's {@code value} is typed, so a websocket message that is not a client message of the
 * {@code graphql-ws} protocol (no or unknown {@code type}) becomes an error envelope carrying the error core reports
 * for it in TypeScript ({@code Unknown message type. Message=[object Object]}); core answers it with
 * {@code connection_error}, as in TypeScript. A body that cannot be read as a GraphQL operation also gives an error
 * envelope.
 */
public final class GraphQLAdapter {

  private static final Set<String> CLIENT_MESSAGE_TYPES = Set.of("connection_init", "start", "stop",
      "connection_terminate");

  private GraphQLAdapter() {}

  /**
   * {@code rawGraphQLRequestToEnvelope(config, request)}.
   *
   * @param config the configuration
   * @param request an {@link ExpressWebSocketMessage} or an HTTP request
   * @return the envelope, or an error envelope
   */
  public static GraphQLRequestEnvelopeOrError rawGraphQLRequestToEnvelope(BoosterConfig config, Object request) {
    return rawGraphQLRequestToEnvelope(config, request, UUID::generate);
  }

  /**
   * {@link #rawGraphQLRequestToEnvelope(BoosterConfig, Object)} with an explicit request id generator (tests).
   *
   * @param config the configuration
   * @param request the raw request
   * @param requestIds generates the request id
   * @return the envelope, or an error envelope
   */
  public static GraphQLRequestEnvelopeOrError rawGraphQLRequestToEnvelope(BoosterConfig config, Object request,
      Supplier<UUID> requestIds) {
    UUID requestID = requestIds.get();
    return request instanceof ExpressWebSocketMessage message
        ? expressWebSocketMessageToEnvelope(config, message, requestID)
        : expressHttpMessageToEnvelope(config, request, requestID);
  }

  private static GraphQLRequestEnvelopeOrError expressWebSocketMessageToEnvelope(BoosterConfig config,
      ExpressWebSocketMessage webSocketRequest, UUID requestID) {
    Logger logger = Loggers.getLogger(config, "graphql-adapter#expressWebSocketMessageToEnvelope");
    logger.debug("Received  WebSocket GraphQL request: ", webSocketRequest);
    EventType eventType = EventType.MESSAGE;
    LocalHttpRequest incomingMessage = webSocketRequest.incomingMessage();
    Map<String, String> headers = incomingMessage == null ? null : incomingMessage.headers();
    Object data = webSocketRequest.data();
    String connectionID = null;
    try {
      ConnectionContext connectionContext = webSocketRequest.connectionContext();
      eventType = connectionContext == null ? null : connectionContext.eventType();
      if (connectionContext != null) {
        if (connectionContext.connectionId() == null) {
          throw new IllegalStateException("Cannot read properties of undefined (reading 'toString')");
        }
        connectionID = connectionContext.connectionId().toString();
      }
      return GraphQLRequestEnvelope.builder()
          .requestID(requestID)
          .eventType(eventType)
          .connectionID(connectionID)
          .token(headers == null ? null : headers.get("authorization"))
          .value(toClientMessage(data))
          .context(ContextEnvelope.of(headers, data, webSocketRequest))
          .build();
    } catch (RuntimeException e) {
      return new GraphQLRequestEnvelopeError(requestID, eventType, connectionID, e);
    }
  }

  private static GraphQLRequestEnvelopeOrError expressHttpMessageToEnvelope(BoosterConfig config, Object httpRequest,
      UUID requestId) {
    Logger logger = Loggers.getLogger(config, "graphql-adapter#expressHttpMessageToEnvelope");
    EventType eventType = EventType.MESSAGE;
    LocalHttpRequest request = HealthAdapter.toHttpRequest(httpRequest);
    Map<String, String> headers = request.headers();
    Object data = request.body();
    try {
      logger.debug("Received GraphQL request: \n- Headers: ", headers, "\n- Body: ", data);
      return GraphQLRequestEnvelope.builder()
          .connectionID(null)
          .requestID(requestId)
          .eventType(eventType)
          .token(headers.get("authorization"))
          .value(toOperation(data))
          .context(ContextEnvelope.of(headers, data, httpRequest))
          .build();
    } catch (RuntimeException e) {
      return new GraphQLRequestEnvelopeError(requestId, eventType, null, e);
    }
  }

  private static GraphQLRequestValue toOperation(Object body) {
    if (body == null) {
      return null;
    }
    if (body instanceof GraphQLRequestValue value) {
      return value;
    }
    if (!(body instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("The GraphQL request body must be a JSON object, but it was: "
          + BoosterJson.toJson(body));
    }
    return BoosterJson.convert(body, GraphQLOperation.class);
  }

  private static GraphQLRequestValue toClientMessage(Object data) {
    if (data == null) {
      return null;
    }
    if (data instanceof GraphQLRequestValue value) {
      return value;
    }
    if (data instanceof Map<?, ?> map && map.get("type") instanceof String type && CLIENT_MESSAGE_TYPES.contains(type)) {
      return BoosterJson.convert(data, GraphQLClientMessage.class);
    }
    throw new IllegalStateException("Unknown message type. Message=" + Js.toJsString(data));
  }
}
