package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.types.AzureHttpFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.AzureWebPubSubFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureWebPubSubRequest;
import com.boostercloud.framework.provider.azure.types.WebPubSubConnectionContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.graphql.GraphQLInit;
import com.boostercloud.framework.types.graphql.GraphQLStart;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/graphql-adapter.test.ts}, plus the Web PubSub and error paths. */
class GraphQLAdapterTest {

  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = ConsoleLogger.INSTANCE;
    config.logLevel = com.boostercloud.framework.types.Level.ERROR;
  }

  /** {@code createMockHttpRequest(body, headers)}. */
  private static AzureHttpRequest createMockHttpRequest(String body, Map<String, String> headers) {
    return AzureHttpRequest.of("POST", "https://test.azurewebsites.net/api/graphql", headers, body);
  }

  private static AzureWebPubSubFunctionInput webPubSub(WebPubSubConnectionContext context, Object data) {
    return new AzureWebPubSubFunctionInput(new AzureWebPubSubRequest(context, data, null),
        AzureInvocationContext.of("inv-1"));
  }

  @Nested
  class TheRawGraphQLRequestToEnvelope {

    @Test
    void generatesAnEnvelopeCorrectlyFromAnAzureHttpRequest() {
      String expectedQuery = "GraphQL query";
      String expectedToken = "token";
      Map<String, Object> expectedBody = obj("query", expectedQuery, "variables", obj("varOne", 3, "varTwo", "test"));
      AzureHttpFunctionInput input = new AzureHttpFunctionInput(
          createMockHttpRequest("{\"query\":\"GraphQL query\",\"variables\":{\"varOne\":3,\"varTwo\":\"test\"}}",
              Map.of("authorization", expectedToken)),
          AzureInvocationContext.of("123"));

      GraphQLRequestEnvelopeOrError gotOutput = GraphQLAdapter.rawGraphQLRequestToEnvelope(config, input);

      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) gotOutput;
      assertThat(envelope.requestID()).isEqualTo(UUID.of("123"));
      assertThat(envelope.eventType()).isEqualTo(EventType.MESSAGE);
      assertThat(envelope.token()).isEqualTo(expectedToken);
      assertThat(envelope.value()).isEqualTo(new GraphQLOperation(expectedQuery, null, null, obj("varOne", 3, "varTwo", "test")));
      assertThat(envelope.context()).isNotNull();
      assertThat(envelope.context().request().body()).isEqualTo(expectedBody);
      assertThat(envelope.context().rawContext()).isSameAs(input);
      assertThat(envelope.connectionID()).isNull();
    }

    @Test
    void copiesTheLowerCasedHeadersAndDropsAnEmptyToken() {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Authorization", "");
      headers.put("Content-Type", "application/json");
      AzureHttpFunctionInput input =
          new AzureHttpFunctionInput(createMockHttpRequest("{\"query\":\"q\"}", headers), AzureInvocationContext.of("1"));

      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config, input);

      assertThat(envelope.token()).isNull();
      assertThat(envelope.context().request().headers())
          .isEqualTo(Map.of("authorization", "", "content-type", "application/json"));
    }

    @Test
    void aNonJsonBodyIsKeptAsTextAndAnEmptyBodyIsAbsent() {
      GraphQLRequestEnvelope text = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config,
          new AzureHttpFunctionInput(createMockHttpRequest("not json", Map.of()), AzureInvocationContext.of("1")));
      GraphQLRequestEnvelope empty = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config,
          new AzureHttpFunctionInput(createMockHttpRequest("", Map.of()), AzureInvocationContext.of("1")));

      assertThat(text.context().request().body()).isEqualTo("not json");
      assertThat(text.value()).isEqualTo(GraphQLOperation.builder().build());
      assertThat(empty.context().request().body()).isNull();
      assertThat(empty.value()).isNull();
    }

    @Test
    void anUnknownInputIsAnErrorEnvelope() {
      GraphQLRequestEnvelopeError error =
          (GraphQLRequestEnvelopeError) GraphQLAdapter.rawGraphQLRequestToEnvelope(config, Map.of("foo", "bar"));

      assertThat(error.requestID()).isEqualTo(UUID.of("unknown"));
      assertThat(error.eventType()).isEqualTo(EventType.MESSAGE);
      assertThat(error.error()).hasMessage("Unknown request type received by GraphQL adapter");
    }
  }

  @Nested
  class WebPubSubRequests {

    @Test
    void systemConnectAndDisconnectMapToTheirEventTypes() {
      GraphQLRequestEnvelope connect = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config,
          webPubSub(new WebPubSubConnectionContext("conn-1", null, "booster", "System", "connect"), null));
      GraphQLRequestEnvelope disconnect = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config,
          webPubSub(new WebPubSubConnectionContext("conn-1", null, "booster", "system", "Disconnect"), null));
      GraphQLRequestEnvelope connected = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config,
          webPubSub(new WebPubSubConnectionContext("conn-1", null, "booster", "system", "connected"), null));

      assertThat(connect.eventType()).isEqualTo(EventType.CONNECT);
      assertThat(connect.connectionID()).isEqualTo("conn-1");
      assertThat(connect.requestID()).isEqualTo(UUID.of("inv-1"));
      assertThat(connect.token()).isNull();
      assertThat(disconnect.eventType()).isEqualTo(EventType.DISCONNECT);
      assertThat(connected.eventType()).isEqualTo(EventType.MESSAGE);
    }

    @Test
    void userMessagesAreParsedIntoClientMessages() {
      AzureWebPubSubFunctionInput input = webPubSub(
          new WebPubSubConnectionContext("conn-1", null, "booster", "user", "message"),
          "{\"type\":\"start\",\"id\":\"1\",\"payload\":{\"query\":\"subscription { a }\"}}");

      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config, input);

      assertThat(envelope.eventType()).isEqualTo(EventType.MESSAGE);
      assertThat(envelope.value()).isEqualTo(new GraphQLStart("1", new GraphQLStart.Payload("subscription { a }", null, null)));
      assertThat(envelope.context().request().headers()).isEqualTo(Map.of());
      assertThat(envelope.context().request().body())
          .isEqualTo(obj("type", "start", "id", "1", "payload", obj("query", "subscription { a }")));
      assertThat(envelope.context().rawContext()).isSameAs(input);
    }

    @Test
    void theConnectionContextMayComeFromTheTriggerMetadata() {
      Map<String, Object> metadata = Map.of("connectionContext",
          obj("connectionId", "conn-2", "hub", "booster", "eventType", "user", "eventName", "message"));
      AzureWebPubSubFunctionInput input = new AzureWebPubSubFunctionInput(
          new AzureWebPubSubRequest(null, obj("type", "connection_init", "payload", obj("Authorization", "Bearer t")),
              "json"),
          new AzureInvocationContext("inv-2", "messages", metadata, null));

      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(config, input);

      assertThat(envelope.connectionID()).isEqualTo("conn-2");
      assertThat(envelope.value()).isEqualTo(new GraphQLInit(new GraphQLInit.Payload("Bearer t")));
    }

    @Test
    void unknownMessagesBecomeTheErrorCoreWouldRaise() {
      GraphQLRequestEnvelopeError unknownType = (GraphQLRequestEnvelopeError) GraphQLAdapter.rawGraphQLRequestToEnvelope(
          config, webPubSub(new WebPubSubConnectionContext("c", null, "booster", "user", "message"),
              "{\"type\":\"subscribe\"}"));
      GraphQLRequestEnvelopeError text = (GraphQLRequestEnvelopeError) GraphQLAdapter.rawGraphQLRequestToEnvelope(
          config, webPubSub(new WebPubSubConnectionContext("c", null, "booster", "user", "message"), "hello"));

      assertThat(unknownType.connectionID()).isEqualTo("c");
      assertThat(unknownType.eventType()).isEqualTo(EventType.MESSAGE);
      assertThat(unknownType.error()).hasMessage("Unknown message type. Message=[object Object]");
      assertThat(text.error()).hasMessage("Unknown message type. Message=hello");
    }

    @Test
    void aMissingConnectionContextIsNotAWebPubSubInput() {
      GraphQLRequestEnvelopeOrError result = GraphQLAdapter.rawGraphQLRequestToEnvelope(config,
          new AzureWebPubSubFunctionInput(new AzureWebPubSubRequest(null, "x", null), AzureInvocationContext.of("1")));

      assertThat(result).isInstanceOfSatisfying(GraphQLRequestEnvelopeError.class,
          error -> assertThat(error.error()).hasMessage("Unknown request type received by GraphQL adapter"));
    }
  }
}
