package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.types.AzureFunctionTypes;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.provider.azure.types.WebPubSubConnectionContext;
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
import com.boostercloud.framework.types.graphql.MessageTypes;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Port of {@code library/graphql-adapter.ts}: HTTP and Web PubSub function inputs to GraphQL request envelopes. It
 * never throws for malformed input; it returns an error envelope instead.
 *
 * <p>Java envelopes type {@code value}: HTTP bodies become a {@link GraphQLOperation} (a truthy non-object body, such
 * as a non-JSON string, becomes an empty operation so core answers {@code Received an empty GraphQL query} as it does
 * in TypeScript), websocket messages a {@link GraphQLClientMessage}. A websocket MESSAGE whose data is not a known
 * client message becomes an error envelope carrying exactly the error core would raise
 * ({@code Unknown message type. Message=<String(data)>}). The raw parsed body stays in
 * {@code context.request.body}.
 */
public final class GraphQLAdapter {

  /** The {@code type}s of the messages a graphql-ws client sends. */
  private static final java.util.Set<String> CLIENT_TYPES = java.util.Set.of(
      MessageTypes.GQL_CONNECTION_INIT.value(), MessageTypes.GQL_START.value(), MessageTypes.GQL_STOP.value(),
      MessageTypes.GQL_CONNECTION_TERMINATE.value());

  private GraphQLAdapter() {}

  /**
   * {@code rawGraphQLRequestToEnvelope}.
   *
   * @param config the configuration
   * @param rawRequest an HTTP or Web PubSub function input
   * @return the envelope, or an error envelope
   */
  public static GraphQLRequestEnvelopeOrError rawGraphQLRequestToEnvelope(BoosterConfig config, Object rawRequest) {
    Logger logger = Loggers.getLogger(config, "graphql-adapter#rawGraphQLRequestToEnvelope");

    // Handle HTTP requests (v4 programming model)
    if (AzureFunctionTypes.isHttpFunctionInput(rawRequest)) {
      return handleHttpRequest(rawRequest, logger);
    }

    // Handle WebSocket requests (Web PubSub v4 programming model)
    if (AzureFunctionTypes.isWebPubSubFunctionInput(rawRequest)) {
      return handleWebPubSubRequest(rawRequest, logger);
    }

    // Fallback error for unknown input types
    IllegalStateException error = new IllegalStateException("Unknown request type received by GraphQL adapter");
    logger.error("Received unknown request type:", rawRequest);
    return new GraphQLRequestEnvelopeError(UUID.of("unknown"), EventType.MESSAGE, null, error);
  }

  private static GraphQLRequestEnvelopeOrError handleHttpRequest(Object input, Logger logger) {
    AzureHttpRequest request = toHttpRequest(get(input, "request"));
    logger.debug("Received GraphQL request: ", request.url());
    UUID requestID = invocationId(input);

    Map<String, String> headers = new LinkedHashMap<>();
    try {
      // Parse the request body
      Object graphQLValue = null;
      try {
        graphQLValue = request.json();
      } catch (RuntimeException notJson) {
        // Body might not be JSON, try text
        String textBody = request.text();
        if (!textBody.isEmpty()) {
          try {
            graphQLValue = BoosterJson.parse(textBody);
          } catch (RuntimeException invalidJson) {
            graphQLValue = textBody;
          }
        }
      }

      // Convert headers to a plain object
      request.forEachHeader((value, key) -> headers.put(key, value));

      String authorization = request.header("authorization");
      return GraphQLRequestEnvelope.builder()
          .requestID(requestID)
          .eventType(EventType.MESSAGE)
          .token(JsValues.truthy(authorization) ? authorization : null)
          .value(toOperation(graphQLValue))
          .context(ContextEnvelope.of(headers, graphQLValue, input))
          .build();
    } catch (RuntimeException error) {
      logger.error("Error processing GraphQL HTTP request: ", error);
      return new GraphQLRequestEnvelopeError(requestID, EventType.MESSAGE, null, error);
    }
  }

  private static GraphQLRequestEnvelopeOrError handleWebPubSubRequest(Object input, Logger logger) {
    Object webPubSubRequest = get(input, "request");
    UUID requestID = invocationId(input);

    // Get connection context from either location (request or triggerMetadata)
    WebPubSubConnectionContext connectionContext = AzureFunctionTypes.getWebPubSubConnectionContext(input);
    if (connectionContext == null) {
      IllegalStateException error =
          new IllegalStateException("WebPubSub connectionContext not found in request or triggerMetadata");
      logger.error("Missing connectionContext. Input structure", JsValues.stringifyPretty(input));
      return new GraphQLRequestEnvelopeError(requestID, EventType.MESSAGE, null, error);
    }

    logger.debug("Received GraphQL WebSocket request: ", connectionContext.eventName());
    String connectionID = connectionContext.connectionId();

    // Map Web PubSub event names to Booster EventType
    EventType eventType = EventType.MESSAGE;
    if (connectionContext.eventType() != null && connectionContext.eventType().toLowerCase(Locale.ROOT).equals("system")) {
      String eventName = connectionContext.eventName() == null
          ? null
          : connectionContext.eventName().toUpperCase(Locale.ROOT);
      if ("CONNECT".equals(eventName) || "DISCONNECT".equals(eventName)) {
        eventType = EventType.valueOf(eventName);
      }
    }

    try {
      // Parse the data if it's a JSON string (Web PubSub sends data as string)
      Object messageData = get(webPubSubRequest, "data");
      if (messageData instanceof String text) {
        try {
          messageData = BoosterJson.parse(text);
          logger.debug("Parsed Web PubSub message data from string:", messageData);
        } catch (RuntimeException notJson) {
          logger.debug("WebPubSub data is not valid JSON, using as-is:", messageData);
        }
      }

      GraphQLRequestValue value = null;
      if (eventType == EventType.MESSAGE && JsValues.truthy(messageData)) {
        value = toClientMessage(messageData);
        if (value == null) {
          return new GraphQLRequestEnvelopeError(requestID, eventType, connectionID,
              new IllegalStateException("Unknown message type. Message=" + JsValues.jsString(messageData)));
        }
      }

      return GraphQLRequestEnvelope.builder()
          .requestID(requestID)
          .connectionID(connectionID)
          .eventType(eventType)
          .value(value)
          .context(ContextEnvelope.of(new LinkedHashMap<String, String>(), messageData, input))
          .build();
    } catch (RuntimeException error) {
      logger.error("Error processing GraphQL WebSocket request: ", error);
      return new GraphQLRequestEnvelopeError(requestID, eventType, connectionID, error);
    }
  }

  /**
   * @param body the parsed HTTP body
   * @return the operation, {@code null} for a falsy body, an empty operation for a truthy non-object body
   */
  static GraphQLOperation toOperation(Object body) {
    if (!JsValues.truthy(body)) {
      return null;
    }
    if (body instanceof Map<?, ?> map) {
      return BoosterJson.convert(map, GraphQLOperation.class);
    }
    return GraphQLOperation.builder().build();
  }

  /**
   * @param data the parsed websocket data
   * @return the client message, or {@code null} when the data is not a known client message
   */
  static GraphQLClientMessage toClientMessage(Object data) {
    if (!(data instanceof Map<?, ?> map) || !(map.get("type") instanceof String type) || !CLIENT_TYPES.contains(type)) {
      return null;
    }
    return BoosterJson.convert(map, GraphQLClientMessage.class);
  }

  private static AzureHttpRequest toHttpRequest(Object request) {
    if (request instanceof AzureHttpRequest typed) {
      return typed;
    }
    return BoosterJson.convert(request, AzureHttpRequest.class);
  }

  private static UUID invocationId(Object input) {
    Object id = get(get(input, "context"), "invocationId");
    return id == null ? null : UUID.of(id.toString());
  }
}
