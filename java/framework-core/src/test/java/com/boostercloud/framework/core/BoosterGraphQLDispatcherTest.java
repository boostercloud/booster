package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.services.graphql.GraphQLGenerator;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.core.services.graphql.GraphQLTestApp;
import com.boostercloud.framework.core.services.graphql.websocketprotocol.GraphQLWebsocketHandler;
import com.boostercloud.framework.core.services.pubsub.NoopReadModelPubSub;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.BoosterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.graphql.GraphQLStart;
import com.boostercloud.framework.types.provider.ProviderConnectionsLibrary;
import com.boostercloud.framework.types.provider.ProviderGraphQLLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;

/**
 * Port of {@code test/booster-graphql-dispatcher.test.ts}. TypeScript replaces graphql-js {@code parse},
 * {@code validate} and {@code execute}; here the operations run on graphql-java against the schema of
 * {@code GraphQLTestApp}, and the collaborators of the resolvers (dispatchers and readers) are mocks.
 */
class BoosterGraphQLDispatcherTest extends BoosterTestSupport {

  private static final UUID REQUEST_ID = UUID.of("request-1");

  private BoosterConfig config;
  private ProviderGraphQLLibrary graphQL;
  private ProviderConnectionsLibrary connections;
  private ProviderReadModelsLibrary readModels;
  private MockedConstruction<BoosterReadModelsReader> readModelsReaders;
  private MockedConstruction<BoosterCommandDispatcher> commandDispatchers;
  private MockedConstruction<BoosterQueryDispatcher> queryDispatchers;
  private MockedConstruction<BoosterEventsReader> eventsReaders;
  private MockedConstruction<BoosterTokenVerifier> tokenVerifiers;
  private MockedConstruction<NoopReadModelPubSub> pubSubs;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    GraphQLGenerator.reset();
    config = Booster.config();
    config.logger = mock(Logger.class);
    GraphQLTestApp.CLASSES.forEach(Decorators::registerClass);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    graphQL = mock(ProviderGraphQLLibrary.class);
    connections = mock(ProviderConnectionsLibrary.class);
    readModels = mock(ProviderReadModelsLibrary.class);
    when(provider.graphQL()).thenReturn(graphQL);
    when(provider.connections()).thenReturn(connections);
    when(provider.readModels()).thenReturn(readModels);
    config.setProvider(provider);
    readModelsReaders = mockConstruction(BoosterReadModelsReader.class);
    commandDispatchers = mockConstruction(BoosterCommandDispatcher.class);
    queryDispatchers = mockConstruction(BoosterQueryDispatcher.class);
    eventsReaders = mockConstruction(BoosterEventsReader.class);
    tokenVerifiers = mockConstruction(BoosterTokenVerifier.class);
    pubSubs = mockConstruction(NoopReadModelPubSub.class,
        (pubSub, context) -> when(pubSub.asyncIterator(any(), any())).thenReturn(Collections.emptyIterator()));
    // The schema (and the collaborators of its resolvers) is generated once per process, before any dispatcher
    GraphQLGenerator.generateSchema(config);
  }

  @AfterEach
  void tearDown() {
    readModelsReaders.close();
    commandDispatchers.close();
    queryDispatchers.close();
    eventsReaders.close();
    tokenVerifiers.close();
    pubSubs.close();
    GraphQLGenerator.reset();
  }

  private BoosterGraphQLDispatcher dispatcherFor(GraphQLRequestEnvelopeOrError envelope) {
    when(graphQL.rawToEnvelope(config, "raw request")).thenReturn(envelope);
    return new BoosterGraphQLDispatcher(config);
  }

  private static GraphQLRequestEnvelope.Builder message() {
    return GraphQLRequestEnvelope.builder().requestID(REQUEST_ID).eventType(EventType.MESSAGE);
  }

  private static GraphQLOperation operation(String query) {
    return GraphQLOperation.builder().query(query).build();
  }

  /** The reader of the first dispatcher (the one of the resolvers was created with the schema). */
  private BoosterReadModelsReader dispatcherReadModelsReader() {
    return readModelsReaders.constructed().get(1);
  }

  private BoosterReadModelsReader generatorReadModelsReader() {
    return readModelsReaders.constructed().getFirst();
  }

  private BoosterQueryDispatcher queryDispatcher() {
    return queryDispatchers.constructed().getFirst();
  }

  private BoosterCommandDispatcher commandDispatcher() {
    return commandDispatchers.constructed().getFirst();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> handledResult(Map<String, String> expectedHeaders) {
    ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);
    verify(graphQL).handleResult(result.capture(), eq(expectedHeaders));
    return (Map<String, Object>) result.getValue();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> errorsOf(Map<String, Object> result) {
    return (List<Map<String, Object>>) result.get("errors");
  }

  @Nested
  class IntrospectionInTheGraphQLApi {

    private static final String INTROSPECTION_QUERY = "{__schema {queryType {name},mutationType { name }  }}";

    @Test
    void isEnabledWithTheDefaultConfig() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(INTROSPECTION_QUERY)).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data",
          Map.of("__schema", Map.of("queryType", Map.of("name", "Query"), "mutationType", Map.of("name", "Mutation")))));
    }

    @Test
    void canBeDisabled() {
      config.enableGraphQLIntrospection = false;
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(INTROSPECTION_QUERY)).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("errors", List.of(Map.of(
          "message", "Instrospection queries are disabled. Check the configuration if you want to enable them.",
          "extensions", Map.of("code", "InvalidProtocolError")))));
    }

    @Test
    void isRecognisedByTheOperationName() {
      config.enableGraphQLIntrospection = false;
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(GraphQLOperation.builder()
          .query("query IntrospectionQuery { __typename }").operationName("IntrospectionQuery").build()).build());

      dispatcher.dispatch("raw request");

      assertThat(errorsOf(handledResult(Map.of())).getFirst().get("message"))
          .isEqualTo("Instrospection queries are disabled. Check the configuration if you want to enable them.");
    }

    @Test
    void doesNotBlockTypeQueries() {
      config.enableGraphQLIntrospection = false;
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message()
          .value(operation("{ __type(name: \"Query\") { name } }")).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data", Map.of("__type", Map.of("name", "Query"))));
    }
  }

  @Nested
  class OnConnect {
    @Test
    void callsHandleResultWithTheGraphQLWebsocketSubprotocolHeaders() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(GraphQLRequestEnvelope.builder().requestID(REQUEST_ID)
          .eventType(EventType.CONNECT).connectionID("connection-1").build());

      dispatcher.dispatch("raw request");

      verify(graphQL).handleResult(null, Map.of("Sec-WebSocket-Protocol", "graphql-ws"));
    }
  }

  @Nested
  class OnDisconnect {
    @Test
    void doesNotDeleteConnectionOrSubscriptionDataWhenThereIsNoConnectionID() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(GraphQLRequestEnvelope.builder().requestID(REQUEST_ID)
          .eventType(EventType.DISCONNECT).build());

      dispatcher.dispatch("raw request");

      verify(dispatcherReadModelsReader(), never()).unsubscribeAll(any());
      verify(connections, never()).deleteData(any(), any());
      verify(graphQL).handleResult(null);
      verify(graphQL, never()).handleResult(any(), any());
    }

    @Test
    void deletesTheConnectionAndThenItsSubscriptions() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(GraphQLRequestEnvelope.builder().requestID(REQUEST_ID)
          .eventType(EventType.DISCONNECT).connectionID("connection-1").build());

      dispatcher.dispatch("raw request");

      InOrder order = inOrder(connections, dispatcherReadModelsReader(), graphQL);
      order.verify(connections).deleteData(config, "connection-1");
      order.verify(dispatcherReadModelsReader()).unsubscribeAll("connection-1");
      order.verify(graphQL).handleResult(null);
    }
  }

  @Test
  void reportsAnUnknownEventType() {
    BoosterGraphQLDispatcher dispatcher = dispatcherFor(GraphQLRequestEnvelope.builder().requestID(REQUEST_ID)
        .build());

    dispatcher.dispatch("raw request");

    verify(graphQL).handleResult(Map.of("errors", List.of(Map.of("message", "Unknown message type null"))));
  }

  @Nested
  class OnMessageThroughTheSocket {

    @Test
    void callsTheWebsocketHandlerWithTheEnvelopeAndMarksTheResponse() {
      GraphQLRequestEnvelope envelope = message().connectionID("connection-1").build();
      try (MockedConstruction<GraphQLWebsocketHandler> handlers = mockConstruction(GraphQLWebsocketHandler.class)) {
        BoosterGraphQLDispatcher dispatcher = dispatcherFor(envelope);

        dispatcher.dispatch("raw request");

        verify(handlers.constructed().getFirst()).handle(envelope);
      }
      verify(graphQL).handleResult(null, Map.of("X-Booster-WebSocket-Message", "true"));
    }

    @Test
    void storesSubscriptionsAndAnswersNothing() {
      UserEnvelope user = UserEnvelope.builder().username("user@example.com").build();
      when(connections.fetchData(config, "connection-1")).thenReturn(java.util.Optional.of(
          new com.boostercloud.framework.types.envelope.ConnectionDataEnvelope(1, user)));
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().connectionID("connection-1")
          .value(new GraphQLStart("operation-1", new GraphQLStart.Payload(
              "subscription { ProductReadModel(id: \"42\") { id sku } }", null, null)))
          .build());

      dispatcher.dispatch("raw request");

      ArgumentCaptor<ReadModelRequestEnvelope> request = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
      ArgumentCaptor<GraphQLOperation> stored = ArgumentCaptor.forClass(GraphQLOperation.class);
      verify(generatorReadModelsReader()).subscribe(eq("connection-1"), request.capture(), stored.capture());
      assertThat(request.getValue().filters()).isEqualTo(Map.of("id", Map.of("eq", "42")));
      assertThat(request.getValue().currentUser()).isEqualTo(user);
      assertThat(stored.getValue()).isEqualTo(new GraphQLOperation(
          "subscription { ProductReadModel(id: \"42\") { id sku } }", "operation-1", null, null));
      verify(connections, never()).sendMessage(any(), any(), any());
      verify(graphQL).handleResult(null, Map.of("X-Booster-WebSocket-Message", "true"));
    }

    @Test
    void sendsTheResultOfAQueryAsDataAndComplete() {
      when(connections.fetchData(config, "connection-1")).thenReturn(java.util.Optional.empty());
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().connectionID("connection-1")
          .value(new GraphQLStart("operation-1", new GraphQLStart.Payload("{ __typename }", null, null)))
          .build());
      dispatcher.dispatch("raw request");

      InOrder order = inOrder(connections);
      order.verify(connections).sendMessage(config, "connection-1",
          new com.boostercloud.framework.types.graphql.GraphQLData("operation-1",
              Map.of("data", Map.of("__typename", "Query"))));
      order.verify(connections).sendMessage(config, "connection-1",
          new com.boostercloud.framework.types.graphql.GraphQLComplete("operation-1"));
    }

    @Test
    void unsubscribesOnStop() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().connectionID("connection-1")
          .value(new com.boostercloud.framework.types.graphql.GraphQLStop("operation-1")).build());

      dispatcher.dispatch("raw request");

      verify(dispatcherReadModelsReader()).unsubscribe("connection-1", "operation-1");
    }

    @Test
    void deletesTheConnectionOnTerminate() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().connectionID("connection-1")
          .value(new com.boostercloud.framework.types.graphql.GraphQLTerminate()).build());

      dispatcher.dispatch("raw request");

      verify(connections).deleteData(config, "connection-1");
      verify(dispatcherReadModelsReader()).unsubscribeAll("connection-1");
    }

    @Test
    void returnsThePublisherOfASubscription() {
      BoosterGraphQLDispatcher dispatcher = new BoosterGraphQLDispatcher(config);

      Object result = dispatcher.runGraphQLOperation(message().connectionID("connection-1")
          .value(operation("subscription { ProductReadModels(filter: { sku: { eq: \"a\" } }) { id } }")).build(),
          null);

      assertThat(result).isInstanceOfAny(org.reactivestreams.Publisher.class, Flow.Publisher.class);
    }

    @Test
    void returnsTheErrorsOfASubscriptionThatCannotBeSetUpWithoutExtensions() {
      BoosterGraphQLDispatcher dispatcher = new BoosterGraphQLDispatcher(config);
      when(generatorReadModelsReader().subscribe(any(), any(), any()))
          .thenThrow(new NotAuthorizedError("Access denied for this resource"));

      Object result = dispatcher.runGraphQLOperation(message().connectionID("connection-1")
          .value(operation("subscription { ProductReadModel(id: \"42\") { id } }")).build(), null);

      assertThat(result).isEqualTo(Map.of("errors", List.of(Map.of(
          "message", "Access denied for this resource",
          "locations", List.of(Map.of("line", 1, "column", 16)),
          "path", List.of("ProductReadModel")))));
    }
  }

  @Nested
  class OnMessageThroughHttp {

    @Test
    void doesNotCallTheWebsocketHandler() {
      try (MockedConstruction<GraphQLWebsocketHandler> handlers = mockConstruction(GraphQLWebsocketHandler.class)) {
        BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().build());

        dispatcher.dispatch("raw request");

        verify(handlers.constructed().getFirst(), never()).handle(any());
      }
    }

    @Test
    void reportsTheErrorOfTheEnvelope() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(new GraphQLRequestEnvelopeError(REQUEST_ID,
          EventType.MESSAGE, null, new IllegalStateException("The request was malformed.")));

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("errors", List.of(Map.of(
          "message", "The request was malformed.", "extensions", Map.of()))));
    }

    @Test
    void reportsAnEmptyBody() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("errors", List.of(Map.of(
          "message", "Received an empty GraphQL body", "extensions", Map.of("code", "InvalidParameterError")))));
    }

    @Test
    void reportsAnEmptyQuery() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(null)).build());

      dispatcher.dispatch("raw request");

      assertThat(errorsOf(handledResult(Map.of())).getFirst().get("message"))
          .isEqualTo("Received an empty GraphQL query");
    }

    @Test
    void rejectsSubscriptionOperations() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message()
          .value(operation("subscription { ProductReadModel(id: \"42\") { id } }")).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("errors", List.of(Map.of(
          "message", "This API and protocol does not support \"subscription\" operations, only \"query\" and "
              + "\"mutation\". Use the socket API for \"subscription\"",
          "extensions", Map.of("code", "InvalidProtocolError")))));
      verify(generatorReadModelsReader(), never()).subscribe(any(), any(), any());
    }

    @Test
    void runsTheOperationWithTheResolverContext() {
      when(queryDispatcher().dispatchQuery(any(), any())).thenReturn(42.0);
      Map<String, Object> variables = Map.of("productId", "product-1");
      String query = "query Stock($productId: ID!) { GetStock(input: { productId: $productId }) }";
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message()
          .value(GraphQLOperation.builder().query(query).variables(variables).build()).build());

      dispatcher.dispatch("raw request");

      ArgumentCaptor<QueryEnvelope> envelope = ArgumentCaptor.forClass(QueryEnvelope.class);
      ArgumentCaptor<GraphQLResolverContext> context = ArgumentCaptor.forClass(GraphQLResolverContext.class);
      verify(queryDispatcher()).dispatchQuery(envelope.capture(), context.capture());
      assertThat(context.getValue().requestID()).isEqualTo(REQUEST_ID);
      assertThat(context.getValue().operation()).isEqualTo(GraphQLOperation.builder().query(query)
          .variables(variables).build());
      assertThat(context.getValue().pubSub()).isInstanceOf(NoopReadModelPubSub.class);
      assertThat(context.getValue().storeSubscriptions()).isTrue();
      assertThat(context.getValue().responseHeaders()).isEmpty();
      assertThat(context.getValue().connectionID()).isNull();
      assertThat(context.getValue().user()).isNull();
      assertThat(envelope.getValue().typeName()).isEqualTo("GetStock");
      assertThat(envelope.getValue().value()).isEqualTo(Map.of("productId", "product-1"));
      assertThat(envelope.getValue().version()).isEqualTo(1);
      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data", Map.of("GetStock", 42.0)));
    }

    @Test
    void passesTheResponseHeadersTheHandlersAdd() {
      when(queryDispatcher().dispatchQuery(any(), any())).thenAnswer(invocation -> {
        GraphQLResolverContext context = invocation.getArgument(1);
        context.responseHeaders().put("Test-Header", "Test-Value");
        return 1.0;
      });
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message()
          .value(operation("{ GetStock(input: { productId: \"product-1\" }) }")).build());

      dispatcher.dispatch("raw request");

      verify(graphQL).handleResult(Map.of("data", Map.of("GetStock", 1.0)), Map.of("Test-Header", "Test-Value"));
    }

    @Test
    void startsTheResponseHeadersWithACopyOfTheDefaultOnes() {
      config.defaultResponseHeaders.put("Access-Control-Allow-Origin", "*");
      when(queryDispatcher().dispatchQuery(any(), any())).thenAnswer(invocation -> {
        GraphQLResolverContext context = invocation.getArgument(1);
        context.responseHeaders().put("Test-Header", "Test-Value");
        return 1.0;
      });
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message()
          .value(operation("{ GetStock(input: { productId: \"product-1\" }) }")).build());

      dispatcher.dispatch("raw request");

      verify(graphQL).handleResult(any(), eq(Map.of("Access-Control-Allow-Origin", "*", "Test-Header", "Test-Value")));
      assertThat(config.defaultResponseHeaders).isEqualTo(Map.of("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void verifiesTheTokenAndRunsTheOperationAsTheUser() {
      UserEnvelope currentUser = UserEnvelope.builder().username("user@example.com").roles(List.of("User"))
          .claims(Map.of()).build();
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().token("the-token")
          .value(operation("{ GetStock(input: { productId: \"product-1\" }) }")).build());
      when(tokenVerifiers.constructed().getFirst().verify("the-token")).thenReturn(currentUser);
      when(queryDispatcher().dispatchQuery(any(), any())).thenReturn(1.0);

      dispatcher.dispatch("raw request");

      ArgumentCaptor<GraphQLResolverContext> context = ArgumentCaptor.forClass(GraphQLResolverContext.class);
      verify(queryDispatcher()).dispatchQuery(any(), context.capture());
      assertThat(context.getValue().user()).isEqualTo(currentUser);
      verify(graphQL).handleResult(Map.of("data", Map.of("GetStock", 1.0)), Map.of());
    }

    @Test
    void reportsATokenThatCannotBeVerified() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().token("bad-token")
          .value(operation("{ GetStock(input: { productId: \"product-1\" }) }")).build());
      when(tokenVerifiers.constructed().getFirst().verify("bad-token"))
          .thenThrow(new NotAuthorizedError("jwt malformed"));

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("errors", List.of(Map.of(
          "message", "jwt malformed", "extensions", Map.of("code", "NotAuthorizedError")))));
      verify(queryDispatcher(), never()).dispatchQuery(any(), any());
    }

    @Test
    void returnsTheErrorsOfAQueryWithTheirExtensions() {
      when(queryDispatcher().dispatchQuery(any(), any()))
          .thenThrow(new NotAuthorizedError("Access denied for this resource"))
          .thenThrow(new BoosterError("Out of stock", "OUT_OF_STOCK", Map.of("available", 0)));
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(
          "query { a: GetStock(input: { productId: \"1\" }) b: GetStock(input: { productId: \"2\" }) }")).build());

      dispatcher.dispatch("raw request");

      Map<String, Object> result = handledResult(Map.of());
      assertThat(result.keySet()).containsExactly("errors", "data");
      assertThat(errorsOf(result)).containsExactly(
          Map.of("message", "Access denied for this resource",
              "locations", List.of(Map.of("line", 1, "column", 9)),
              "path", List.of("a"),
              "extensions", Map.of("code", "NotAuthorizedError")),
          Map.of("message", "Out of stock",
              "locations", List.of(Map.of("line", 1, "column", 48)),
              "path", List.of("b"),
              "extensions", Map.of("code", "OUT_OF_STOCK", "data", Map.of("available", 0))));
      assertThat(result.get("data")).isNull();
    }

    @Test
    void returnsTheErrorsOfAMutationWithTheirExtensions() {
      when(commandDispatcher().dispatchCommand(any(), any())).thenThrow(new IllegalStateException("Boom"));
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(
          "mutation { CreateProduct(input: { sku: \"a\", price: 1 }) }")).build());

      dispatcher.dispatch("raw request");

      assertThat(errorsOf(handledResult(Map.of()))).containsExactly(Map.of("message", "Boom",
          "locations", List.of(Map.of("line", 1, "column", 12)), "path", List.of("CreateProduct"),
          "extensions", Map.of()));
    }

    @Test
    void returnsTrueForCommandsThatReturnNothing() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(
          "mutation { DeleteProduct(input: { productId: \"1\" }) }")).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data", Map.of("DeleteProduct", true)));
      ArgumentCaptor<CommandEnvelope> envelope = ArgumentCaptor.forClass(CommandEnvelope.class);
      verify(commandDispatcher()).dispatchCommand(envelope.capture(), any());
      assertThat(envelope.getValue().typeName()).isEqualTo("DeleteProduct");
      assertThat(envelope.getValue().value()).isEqualTo(Map.of("productId", "1"));
    }

    @Test
    void returnsWhatTheCommandReturns() {
      when(commandDispatcher().dispatchCommand(any(), any())).thenReturn(UUID.of("product-9"));
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(
          "mutation { CreateProduct(input: { sku: \"a\", price: 1 }) }")).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data", Map.of("CreateProduct", "product-9")));
    }

    @Test
    void passesTheRequestContextToTheEnvelopes() {
      ContextEnvelope requestContext = ContextEnvelope.of(Map.of("authorization", "Bearer 123"),
          Map.of("query", "Test query"), "raw");
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().context(requestContext).value(operation(
          "mutation { DeleteProduct(input: { productId: \"1\" }) }")).build());

      dispatcher.dispatch("raw request");

      ArgumentCaptor<CommandEnvelope> envelope = ArgumentCaptor.forClass(CommandEnvelope.class);
      ArgumentCaptor<GraphQLResolverContext> context = ArgumentCaptor.forClass(GraphQLResolverContext.class);
      verify(commandDispatcher()).dispatchCommand(envelope.capture(), context.capture());
      assertThat(envelope.getValue().context().request()).isEqualTo(requestContext.request());
      assertThat(envelope.getValue().context().rawContext()).isSameAs(context.getValue());
      assertThat(context.getValue().context()).isSameAs(requestContext);
    }

    @Test
    void reportsSyntaxErrorsWithTheirLocation() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation("query {")).build());

      dispatcher.dispatch("raw request");

      Map<String, Object> error = errorsOf(handledResult(Map.of())).getFirst();
      assertThat(error.keySet()).containsExactly("message", "locations", "extensions");
      assertThat(error.get("extensions")).isEqualTo(Map.of());
    }

    @Test
    void reportsEveryValidationError() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation("{ foo bar }")).build());

      dispatcher.dispatch("raw request");

      List<Map<String, Object>> errors = errorsOf(handledResult(Map.of()));
      assertThat(errors).hasSize(2);
      assertThat(errors).allSatisfy(error -> {
        assertThat(error.keySet()).containsExactly("message", "locations", "extensions");
        assertThat(error.get("extensions")).isEqualTo(Map.of());
      });
    }

    @Test
    void requiresTheOperationNameWithSeveralOperations() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(
          "query A { __typename } query B { __typename }")).build());

      dispatcher.dispatch("raw request");

      assertThat(errorsOf(handledResult(Map.of())).getFirst()).isEqualTo(Map.of("message",
          "Could not extract GraphQL operation. Be sure to either send only one query, mutation, or subscription, or, "
              + "in case you send several operations, include the \"operationName\" field",
          "extensions", Map.of("code", "InvalidParameterError")));
    }

    @Test
    void runsTheNamedOperation() {
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(GraphQLOperation.builder()
          .query("query A { a: __typename } query B { b: __typename }").operationName("B").build()).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data", Map.of("b", "Query")));
    }

    @Test
    void readsReadModelsWithTheSelectedFields() {
      when(generatorReadModelsReader().findById(any())).thenReturn(Map.of("id", "42", "sku", "SKU-42",
          "addresses", List.of(Map.of("street", "Main St", "zip", 1))));
      BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation(
          "{ ProductReadModel(id: \"42\") { id sku addresses { street } } }")).build());

      dispatcher.dispatch("raw request");

      assertThat(handledResult(Map.of())).isEqualTo(Map.of("data", Map.of("ProductReadModel", Map.of("id", "42",
          "sku", "SKU-42", "addresses", List.of(Map.of("street", "Main St"))))));
      ArgumentCaptor<ReadModelRequestEnvelope> request = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
      verify(generatorReadModelsReader()).findById(request.capture());
      assertThat(request.getValue().key()).isEqualTo(new ReadModelRequestEnvelope.Key(UUID.of("42"), null));
    }
  }

  @Test
  void reportsAnInvalidSchemaOnEveryOperationButStillHandlesConnections() {
    GraphQLGenerator.reset();
    config.entities.clear();
    config.reducers.clear();
    BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation("{ __typename }")).build());

    dispatcher.dispatch("raw request");

    Map<String, Object> error = errorsOf(handledResult(Map.of())).getFirst();
    assertThat((String) error.get("message")).contains("EntityType");
    assertThat(error.get("extensions")).isEqualTo(Map.of());
  }

  @Test
  void tracesTheDispatchAndTheOperation() {
    List<String> started = new ArrayList<>();
    config.traceConfiguration.enableAll();
    config.traceConfiguration.onStart = (traceConfig, actionType, traceInfo) -> started.add(actionType + ":"
        + traceInfo.methodName);
    BoosterGraphQLDispatcher dispatcher = dispatcherFor(message().value(operation("{ __typename }")).build());

    dispatcher.dispatch("raw request");

    assertThat(started).containsExactly("GRAPHQL_DISPATCH:dispatch", "GRAPHQL_RUN_OPERATION:runGraphQLOperation");
  }
}
