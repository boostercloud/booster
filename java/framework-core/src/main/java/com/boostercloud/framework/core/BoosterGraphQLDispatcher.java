package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.instrumentation.Tracer;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.services.graphql.GraphQLExecution;
import com.boostercloud.framework.core.services.graphql.GraphQLGenerator;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.core.services.graphql.websocketprotocol.GraphQLWebsocketHandler;
import com.boostercloud.framework.core.services.graphql.websocketprotocol.GraphQLWebsocketHandlerCallbacks;
import com.boostercloud.framework.core.services.pubsub.NoopReadModelPubSub;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.InvalidProtocolError;
import com.boostercloud.framework.types.graphql.GraphQLWebsocketMessages;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.InvalidSchemaException;
import graphql.validation.ValidationError;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Port of {@code booster-graphql-dispatcher.ts}: the entry point of every GraphQL request, over HTTP and websockets
 * ({@code boosterServeGraphQL} creates one dispatcher per request; the schema is generated once per process).
 *
 * <ul>
 *   <li>{@code CONNECT}: {@code handleResult(null, {Sec-WebSocket-Protocol: graphql-ws})}; nothing is stored;</li>
 *   <li>{@code MESSAGE}: the response headers start as a copy of {@code config.defaultResponseHeaders} (plus
 *       {@code X-Booster-WebSocket-Message: true} for websocket messages); the token is verified (a failure becomes
 *       the error of the request); websocket messages go to the {@link GraphQLWebsocketHandler} (result
 *       {@code null}), HTTP operations are run and their result is handed to {@code handleResult(result, headers)}
 *       with the headers the handlers added;</li>
 *   <li>{@code DISCONNECT}: the connection data and all its subscriptions are deleted, then
 *       {@code handleResult(null)} (without headers);</li>
 *   <li>any other event type: {@code handleResult({errors: [{message: "Unknown message type <type>"}]})}.</li>
 * </ul>
 *
 * <p>Results are JSON maps shaped like graphql-js results ({@code errors} first when there are any, then
 * {@code data}); every error carries {@code extensions} with the {@code code} and {@code data} of the original
 * {@code BoosterError} ({@link GraphQLExecution#formatError(Object, boolean)}). Traced as {@code GRAPHQL_DISPATCH}
 * ({@link #dispatch}) and {@code GRAPHQL_RUN_OPERATION}.
 */
public class BoosterGraphQLDispatcher {

  /** The response header marking the answer to a websocket {@code MESSAGE}. */
  public static final String WEBSOCKET_MESSAGE_HEADER = "X-Booster-WebSocket-Message";

  private final BoosterConfig config;
  private final BoosterReadModelsReader readModelDispatcher;
  private final GraphQLSchema graphQLSchema;
  private final InvalidSchemaException schemaFailure;
  private final BoosterTokenVerifier boosterTokenVerifier;
  private final GraphQLWebsocketHandler websocketHandler;

  /**
   * @param config the configuration
   */
  public BoosterGraphQLDispatcher(BoosterConfig config) {
    this.config = config;
    this.readModelDispatcher = new BoosterReadModelsReader(config);
    GraphQLSchema schema;
    InvalidSchemaException failure;
    try {
      schema = GraphQLGenerator.generateSchema(config);
      failure = null;
    } catch (InvalidSchemaException e) {
      // graphql-js only reports an invalid schema when an operation is validated: keep it for every operation
      schema = null;
      failure = e;
    }
    this.graphQLSchema = schema;
    this.schemaFailure = failure;
    this.boosterTokenVerifier = new BoosterTokenVerifier(config);
    this.websocketHandler = new GraphQLWebsocketHandler(config, config.provider().connections(),
        new GraphQLWebsocketHandlerCallbacks() {
          @Override
          public Object onStartOperation(GraphQLRequestEnvelope envelope) {
            return runGraphQLOperation(envelope, new LinkedHashMap<>());
          }

          @Override
          public void onStopOperation(String connectionID, String messageID) {
            readModelDispatcher.unsubscribe(connectionID, messageID);
          }

          @Override
          public void onTerminate(String connectionID) {
            handleDisconnect(connectionID);
          }
        },
        boosterTokenVerifier);
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatch(request)}.
   *
   * @param request the provider-specific request
   * @return the provider-specific response ({@code config.provider.graphQL.handleResult(...)})
   */
  public Object dispatch(Object request) {
    return Tracer.trace(TraceActionTypes.GRAPHQL_DISPATCH.value(), BoosterGraphQLDispatcher.class, "dispatch",
        Arrays.asList(request), () -> doDispatch(request));
  }

  private Object doDispatch(Object request) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#dispatch");
    GraphQLRequestEnvelopeOrError envelopeOrError = config.provider().graphQL().rawToEnvelope(config, request);
    logger.debug("Received the following GraphQL envelope: ", envelopeOrError);

    if (envelopeOrError.eventType() == null) {
      return unknownMessageType(null);
    }
    return switch (envelopeOrError.eventType()) {
      case CONNECT -> config.provider().graphQL().handleResult(null,
          GraphQLWebsocketMessages.GRAPHQL_WEBSOCKET_SUBPROTOCOL_HEADERS);
      case MESSAGE -> {
        Map<String, String> responseHeaders = new LinkedHashMap<>(config.defaultResponseHeaders);
        // Mark WebSocket MESSAGE requests so providers can return the appropriate format
        if (Js.isTruthy(envelopeOrError.connectionID())) {
          responseHeaders.put(WEBSOCKET_MESSAGE_HEADER, "true");
        }
        Object result = handleMessage(envelopeOrError, responseHeaders);
        yield config.provider().graphQL().handleResult(result, responseHeaders);
      }
      case DISCONNECT -> {
        handleDisconnect(envelopeOrError.connectionID());
        yield config.provider().graphQL().handleResult(null);
      }
    };
  }

  private Object unknownMessageType(Object eventType) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("message", "Unknown message type " + eventType);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("errors", List.of(error));
    return config.provider().graphQL().handleResult(result);
  }

  private GraphQLRequestEnvelopeOrError verifyTokenFromEnvelope(GraphQLRequestEnvelopeOrError envelopeOrError) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#verifyTokenFromEnvelop");
    if (envelopeOrError instanceof GraphQLRequestEnvelope envelope && Js.isTruthy(envelope.token())) {
      try {
        logger.debug("Decoding current user from auth token: " + envelope.token());
        return envelope.toBuilder().currentUser(boosterTokenVerifier.verify(envelope.token())).build();
      } catch (RuntimeException e) {
        logger.debug("Unable to decode auth token");
        return new GraphQLRequestEnvelopeError(envelope.requestID(), envelope.eventType(), envelope.connectionID(), e);
      }
    }
    return envelopeOrError;
  }

  private Object handleMessage(GraphQLRequestEnvelopeOrError envelope, Map<String, String> responseHeaders) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#handleMessage");
    logger.debug("Starting GraphQL operation:", envelope);

    GraphQLRequestEnvelopeOrError envelopeOrError = verifyTokenFromEnvelope(envelope);

    if (cameThroughSocket(envelopeOrError.connectionID())) {
      websocketHandler.handle(envelopeOrError);
      return null;
    }
    return runGraphQLOperation(envelopeOrError, responseHeaders);
  }

  /**
   * {@code runGraphQLOperation(envelope, responseHeaders)} (private in TypeScript, the {@code onStartOperation}
   * callback of the websocket handler): never throws; a failure is returned as {@code {errors: [...]}}.
   *
   * @param envelope the request or the error to report
   * @param responseHeaders the response headers the handlers may add to (shared by reference)
   * @return the result map of a query or mutation, or the publisher of results of a subscription
   */
  Object runGraphQLOperation(GraphQLRequestEnvelopeOrError envelope, Map<String, String> responseHeaders) {
    return Tracer.trace(TraceActionTypes.GRAPHQL_RUN_OPERATION.value(), BoosterGraphQLDispatcher.class,
        "runGraphQLOperation", Arrays.asList(envelope, responseHeaders),
        () -> doRunGraphQLOperation(envelope, responseHeaders == null ? new LinkedHashMap<>() : responseHeaders));
  }

  private Object doRunGraphQLOperation(GraphQLRequestEnvelopeOrError envelopeOrError,
      Map<String, String> responseHeaders) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#runGraphQLOperation");
    try {
      if (envelopeOrError instanceof GraphQLRequestEnvelopeError envelopeError) {
        throw envelopeError.error();
      }
      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) envelopeOrError;
      if (envelope.value() == null) {
        throw new InvalidParameterError("Received an empty GraphQL body");
      }
      if (!(envelope.value() instanceof GraphQLOperation operation) || !Js.isTruthy(operation.query())) {
        throw new InvalidParameterError("Received an empty GraphQL query");
      }

      Document queryDocument = Parser.parse(operation.query());
      List<GraphQLError> errors = validate(queryDocument);
      if (!errors.isEmpty()) {
        throw new GraphQLErrorsException(errors);
      }
      OperationDefinition operationData = getOperationAST(queryDocument, operation.operationName());
      boolean isIntrospectionQuery = "IntrospectionQuery".equals(operation.operationName())
          || operation.query().contains("__schema");
      if (isIntrospectionQuery && !config.enableGraphQLIntrospection) {
        throw new InvalidProtocolError(
            "Instrospection queries are disabled. Check the configuration if you want to enable them.");
      }

      if (operationData == null) {
        throw new InvalidParameterError("Could not extract GraphQL operation. "
            + "Be sure to either send only one query, mutation, or subscription, or, in case you send several "
            + "operations, include the \"operationName\" field");
      }
      GraphQLResolverContext resolverContext = new GraphQLResolverContext(
          envelope.connectionID(),
          responseHeaders,
          operation.toBuilder().build(),
          envelope.requestID(),
          envelope.currentUser(),
          true,
          new NoopReadModelPubSub(),
          envelope.context());

      return switch (operationData.getOperation()) {
        case QUERY, MUTATION -> handleQueryOrMutation(queryDocument, resolverContext);
        case SUBSCRIPTION -> handleSubscription(queryDocument, resolverContext);
      };
    } catch (Throwable e) {
      List<?> errors = e instanceof GraphQLErrorsException list ? list.errors() : List.of(e);
      logger.error(e instanceof GraphQLErrorsException ? errors : e);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("errors", GraphQLExecution.formatErrors(errors, true));
      return result;
    }
  }

  /** {@code graphql.validate(schema, document)}, which first asserts that the schema is valid. */
  private List<GraphQLError> validate(Document queryDocument) {
    if (schemaFailure != null) {
      throw schemaFailure;
    }
    List<ValidationError> validationErrors = GraphQLExecution.validate(graphQLSchema, queryDocument);
    return new ArrayList<>(validationErrors);
  }

  private Map<String, Object> handleQueryOrMutation(Document queryDocument, GraphQLResolverContext resolverContext) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#handleQueryOrMutation");
    ExecutionResult result = GraphQLExecution.execute(graphQLSchema, queryDocument,
        resolverContext.operation().query(), resolverContext, resolverContext.operation().variables(),
        resolverContext.operation().operationName());
    Map<String, Object> formatted = GraphQLExecution.toResultMap(result, true);
    logger.debug("GraphQL result: ", formatted);
    return formatted;
  }

  private Object handleSubscription(Document queryDocument, GraphQLResolverContext resolverContext) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#handleSubscription");
    if (!cameThroughSocket(resolverContext.connectionID())) {
      throw new InvalidProtocolError("This API and protocol does not support \"subscription\" operations, only "
          + "\"query\" and \"mutation\". Use the socket API for \"subscription\"");
    }
    ExecutionResult result = GraphQLExecution.execute(graphQLSchema, queryDocument,
        resolverContext.operation().query(), resolverContext, resolverContext.operation().variables(),
        resolverContext.operation().operationName());
    logger.debug("GraphQL subscription finished");
    Object data = result.getData();
    if ((data instanceof org.reactivestreams.Publisher<?> || data instanceof Flow.Publisher<?>)
        && (result.getErrors() == null || result.getErrors().isEmpty())) {
      return data;
    }
    // The subscription could not be set up: graphql-js returns `{ errors }` as they are (no Booster extensions)
    Map<String, Object> errors = new LinkedHashMap<>();
    errors.put("errors", GraphQLExecution.formatErrors(result.getErrors(), false));
    return errors;
  }

  private void handleDisconnect(String connectionID) {
    Logger logger = Loggers.getLogger(config, "BoosterGraphQLDispatcher#handleDisconnect");
    if (!Js.isTruthy(connectionID)) {
      // This should be impossible, but just in case
      logger.info("Received a DISCONNECT message but field 'connectionID' is missing. Doing nothing");
      return;
    }
    logger.debug("Deleting all subscriptions and connection data");
    config.provider().connections().deleteData(config, connectionID);
    readModelDispatcher.unsubscribeAll(connectionID);
  }

  /** {@code connectionID != undefined}: a websocket message, even with an empty id. */
  private static boolean cameThroughSocket(String connectionID) {
    return connectionID != null;
  }

  /**
   * graphql-js {@code getOperationAST(document, operationName)}: without a name, the only operation of the document
   * ({@code null} when there are several); with a name, the operation with that name ({@code null} when none).
   *
   * @param document the document
   * @param operationName the operation name, or {@code null}
   * @return the operation, or {@code null}
   */
  static OperationDefinition getOperationAST(Document document, String operationName) {
    OperationDefinition operation = null;
    for (Definition<?> definition : document.getDefinitions()) {
      if (definition instanceof OperationDefinition operationDefinition) {
        if (operationName == null) {
          // If no operation name was provided, only return an Operation if there is one defined in the document.
          // Upon encountering the second, return null.
          if (operation != null) {
            return null;
          }
          operation = operationDefinition;
        } else if (Objects.equals(operationDefinition.getName(), operationName)) {
          return operationDefinition;
        }
      }
    }
    return operation;
  }

  /** The array of validation errors graphql-js throws ({@code throw errors}). */
  private static final class GraphQLErrorsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<GraphQLError> errors;

    private GraphQLErrorsException(List<GraphQLError> errors) {
      super(errors.isEmpty() ? "GraphQL errors" : errors.getFirst().getMessage(), null, false, false);
      this.errors = List.copyOf(errors);
    }

    private List<GraphQLError> errors() {
      return errors;
    }
  }
}
