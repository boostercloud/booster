package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.graphql.GraphQLInit;
import com.boostercloud.framework.types.graphql.GraphQLStart;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/graphql-adapter.test.ts}, plus the websocket branch. */
class GraphQLAdapterTest {

  private BoosterConfig mockConfig;
  private UUID mockUuid;
  private String mockUserToken;
  private Map<String, Object> mockBody;
  private LocalHttpRequest mockRequest;

  @BeforeEach
  void setUp() {
    mockConfig = new BoosterConfig("test");
    mockConfig.logger = mock(Logger.class);
    mockUuid = UUID.of(Mocks.uuid());
    mockUserToken = Mocks.uuid();
    mockBody = map("query", "", "variables", map());
    mockRequest = LocalHttpRequest.of("POST", "/", Map.of("authorization", mockUserToken), mockBody);
  }

  @Nested
  class HttpRequests {
    @Test
    void shouldCallLoggerDebug() {
      GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig, mockRequest, () -> mockUuid);
      verify(mockConfig.logger, times(1)).debug("[Booster]|graphql-adapter#expressHttpMessageToEnvelope: ",
          "Received GraphQL request: \n- Headers: ", mockRequest.headers(), "\n- Body: ", mockRequest.body());
    }

    @Test
    void shouldGenerateTheExpectedEnvelope() {
      GraphQLRequestEnvelopeOrError result =
          GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig, mockRequest, () -> mockUuid);
      assertThat(result).isEqualTo(GraphQLRequestEnvelope.builder()
          .requestID(mockUuid)
          .eventType(EventType.MESSAGE)
          .connectionID(null)
          .token(mockUserToken)
          .value(new GraphQLOperation("", null, null, map()))
          .context(ContextEnvelope.of(mockRequest.headers(), mockRequest.body(), mockRequest))
          .build());
    }

    @Test
    void generatesARandomRequestIdAndAcceptsRawMapRequests() {
      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          map("headers", map("authorization", "Bearer x"), "body", map("query", "{ a }", "operationName", "Op")));
      assertThat(envelope.requestID()).isNotNull();
      assertThat(envelope.token()).isEqualTo("Bearer x");
      assertThat(envelope.value()).isEqualTo(new GraphQLOperation("{ a }", null, "Op", null));
    }

    @Test
    void withoutBodyOrAuthorization() {
      GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          LocalHttpRequest.of("POST", "/", Map.of(), null));
      assertThat(envelope.value()).isNull();
      assertThat(envelope.token()).isNull();
    }

    @Test
    void aBodyThatIsNotAnObjectGivesAnErrorEnvelope() {
      GraphQLRequestEnvelopeOrError result = GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          LocalHttpRequest.of("POST", "/", Map.of(), java.util.List.of(1)), () -> mockUuid);
      assertThat(result).isInstanceOf(GraphQLRequestEnvelopeError.class);
      assertThat(result.requestID()).isEqualTo(mockUuid);
      assertThat(result.eventType()).isEqualTo(EventType.MESSAGE);
      assertThat(result.connectionID()).isNull();
    }
  }

  @Nested
  class WebSocketMessages {
    private final UUID connectionId = UUID.of("conn-1");
    private final LocalHttpRequest upgrade =
        LocalHttpRequest.of("GET", "/websocket", Map.of("authorization", "Bearer ws"), null);

    @Test
    void connectMessageAndDisconnect() {
      ExpressWebSocketMessage connect =
          new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.CONNECT), upgrade, null);
      assertThat(GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig, connect, () -> mockUuid))
          .isEqualTo(GraphQLRequestEnvelope.builder().requestID(mockUuid).eventType(EventType.CONNECT)
              .connectionID("conn-1").token("Bearer ws")
              .context(ContextEnvelope.of(upgrade.headers(), null, connect)).build());
      verify(mockConfig.logger).debug("[Booster]|graphql-adapter#expressWebSocketMessageToEnvelope: ",
          "Received  WebSocket GraphQL request: ", connect);

      ExpressWebSocketMessage start = new ExpressWebSocketMessage(
          new ConnectionContext(connectionId, EventType.MESSAGE), upgrade,
          map("type", "start", "id", "1", "payload", map("query", "subscription { a }", "variables", map())));
      GraphQLRequestEnvelope startEnvelope =
          (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig, start);
      assertThat(startEnvelope.value())
          .isEqualTo(new GraphQLStart("1", new GraphQLStart.Payload("subscription { a }", null, map())));
      assertThat(startEnvelope.context().request().body()).isEqualTo(start.data());

      GraphQLRequestEnvelope init = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.MESSAGE), upgrade,
              map("type", "connection_init", "payload", map("Authorization", "Bearer t"))));
      assertThat(init.value()).isEqualTo(new GraphQLInit(new GraphQLInit.Payload("Bearer t")));

      GraphQLRequestEnvelope disconnect = (GraphQLRequestEnvelope) GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.DISCONNECT), null, null));
      assertThat(disconnect.eventType()).isEqualTo(EventType.DISCONNECT);
      assertThat(disconnect.connectionID()).isEqualTo("conn-1");
      assertThat(disconnect.token()).isNull();
      assertThat(disconnect.context().request().headers()).isNull();
    }

    @Test
    void messagesOutsideTheProtocolBecomeTheErrorCoreReports() {
      GraphQLRequestEnvelopeOrError unknown = GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.MESSAGE), upgrade,
              map("type", "ka")), () -> mockUuid);
      assertThat(unknown).isInstanceOfSatisfying(GraphQLRequestEnvelopeError.class, error -> {
        assertThat(error.connectionID()).isEqualTo("conn-1");
        assertThat(error.eventType()).isEqualTo(EventType.MESSAGE);
        assertThat(error.error()).hasMessage("Unknown message type. Message=[object Object]");
      });
      GraphQLRequestEnvelopeOrError text = GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          new ExpressWebSocketMessage(new ConnectionContext(connectionId, EventType.MESSAGE), upgrade, "hello"));
      assertThat(((GraphQLRequestEnvelopeError) text).error()).hasMessage("Unknown message type. Message=hello");
    }

    @Test
    void aMissingConnectionIdGivesAnErrorEnvelope() {
      GraphQLRequestEnvelopeOrError result = GraphQLAdapter.rawGraphQLRequestToEnvelope(mockConfig,
          new ExpressWebSocketMessage(new ConnectionContext(null, EventType.CONNECT), upgrade, null), () -> mockUuid);
      assertThat(result).isEqualTo(new GraphQLRequestEnvelopeError(mockUuid, EventType.CONNECT, null,
          ((GraphQLRequestEnvelopeError) result).error()));
    }
  }
}
