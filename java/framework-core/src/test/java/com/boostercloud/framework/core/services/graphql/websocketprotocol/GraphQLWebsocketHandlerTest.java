package com.boostercloud.framework.core.services.graphql.websocketprotocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.BoosterTokenVerifier;
import com.boostercloud.framework.core.services.graphql.IteratorPublisher;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeError;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** Port of {@code test/services/graphql/websocket-protocol/graphql-websocket-protocol.test.ts}. */
class GraphQLWebsocketHandlerTest {

  private static final Instant NOW = Instant.parse("2024-01-31T10:20:30.123Z");

  private BoosterConfig config;
  private Logger logger;
  private ProviderConnectionsLibrary connectionsManager;
  private GraphQLWebsocketHandlerCallbacks callbacks;
  private BoosterTokenVerifier boosterTokenVerifier;
  private GraphQLWebsocketHandler websocketHandler;
  private GraphQLRequestEnvelope.Builder envelope;

  @BeforeEach
  void setUp() {
    Dates.useClock(Clock.fixed(NOW, ZoneOffset.UTC));
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    connectionsManager = mock(ProviderConnectionsLibrary.class);
    callbacks = mock(GraphQLWebsocketHandlerCallbacks.class);
    boosterTokenVerifier = mock(BoosterTokenVerifier.class);
    websocketHandler = new GraphQLWebsocketHandler(config, connectionsManager, callbacks, boosterTokenVerifier);
    envelope = GraphQLRequestEnvelope.builder().requestID(UUID.of("request-1")).eventType(EventType.MESSAGE);
  }

  @AfterEach
  void restoreClock() {
    Dates.resetClock();
  }

  /** The handle method must never fail: it only logs or sends the error to the connection. */
  private void handle(Object envelopeOrError) {
    assertThatCode(() -> websocketHandler.handle(
        (com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError) envelopeOrError))
        .doesNotThrowAnyException();
  }

  @Test
  void justLogsAnErrorWithAnEnvelopeWithNoConnectionID() {
    handle(envelope.connectionID(null).build());

    verify(logger).error(anyString(), eq("Missing websocket connectionID"));
    verifyNoInteractions(connectionsManager);
  }

  @Test
  void justLogsAnErrorWithAnEmptyConnectionID() {
    handle(envelope.connectionID("").value(new GraphQLTerminate()).build());

    verify(logger).error(anyString(), eq("Missing websocket connectionID"));
    verifyNoInteractions(connectionsManager, callbacks);
  }

  @Nested
  class WithAConnectionID {

    private static final String CONNECTION_ID = "connection-1";

    @BeforeEach
    void setConnection() {
      envelope.connectionID(CONNECTION_ID);
    }

    @Test
    void sendsTheErrorOfAnErrorEnvelopeToTheClient() {
      handle(new GraphQLRequestEnvelopeError(UUID.of("request-1"), EventType.MESSAGE, CONNECTION_ID,
          new IllegalStateException("Something went wrong.")));

      verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitError("Something went wrong."));
    }

    @Test
    void sendsTheRightErrorWithAnEmptyValue() {
      handle(envelope.value(null).build());

      verify(connectionsManager).sendMessage(config, CONNECTION_ID,
          new GraphQLInitError("Received an empty GraphQL body"));
    }

    @Test
    void sendsAnUnknownMessageErrorWhenTheValueIsNotAWebsocketMessage() {
      handle(envelope.value(GraphQLOperation.builder().query("{ a }").build()).build());

      verify(connectionsManager).sendMessage(config, CONNECTION_ID,
          new GraphQLInitError("Unknown message type. Message=[object Object]"));
    }

    @Nested
    class WithAConnectionInitMessage {

      @BeforeEach
      void setMessage() {
        envelope.value(new GraphQLInit(new GraphQLInit.Payload(null)));
      }

      @Test
      void sendsBackAConnectionAck() {
        handle(envelope.build());

        verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitAck());
      }

      @Test
      void storesConnectionDataBeforeTheAck() {
        handle(envelope.build());

        InOrder order = inOrder(connectionsManager);
        order.verify(connectionsManager).storeData(config, CONNECTION_ID,
            new ConnectionDataEnvelope(NOW.getEpochSecond() + 604800, null));
        order.verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitAck());
        verifyNoInteractions(boosterTokenVerifier);
      }

      @Test
      void storesConnectionDataIncludingTheUserWithAnAccessToken() {
        UserEnvelope expectedUser = UserEnvelope.builder().username("user@example.com").roles(List.of("User"))
            .claims(Map.of()).build();
        when(boosterTokenVerifier.verify("the-token")).thenReturn(expectedUser);
        envelope.value(new GraphQLInit(new GraphQLInit.Payload("the-token")));

        handle(envelope.build());

        verify(connectionsManager).storeData(config, CONNECTION_ID,
            new ConnectionDataEnvelope(NOW.getEpochSecond() + 604800, expectedUser));
      }

      @Test
      void usesTheConfiguredConnectionDuration() {
        config.subscriptions.maxConnectionDurationInSeconds = 10;

        handle(envelope.build());

        verify(connectionsManager).storeData(config, CONNECTION_ID,
            new ConnectionDataEnvelope(NOW.getEpochSecond() + 10, null));
      }

      @Test
      void sendsAConnectionErrorAndStoresNothingWhenTheTokenIsInvalid() {
        when(boosterTokenVerifier.verify("bad-token")).thenThrow(new NotAuthorizedError("invalid token"));
        envelope.value(new GraphQLInit(new GraphQLInit.Payload("bad-token")));

        handle(envelope.build());

        verify(connectionsManager, never()).storeData(any(), any(), any());
        verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitError("invalid token"));
      }
    }

    @Nested
    class WithAStartMessage {

      private final GraphQLStart.Payload payload = new GraphQLStart.Payload("query { a }", "AnOperation",
          Map.of("aField", "value"));

      @BeforeEach
      void setMessage() {
        envelope.value(new GraphQLStart("operation-1", payload));
      }

      @Test
      void failsIfThereIsNoId() {
        handle(envelope.value(new GraphQLStart(null, payload)).build());

        verify(connectionsManager).sendMessage(config, CONNECTION_ID,
            new GraphQLInitError("Missing \"id\" in start message"));
        verifyNoInteractions(callbacks);
      }

      @Test
      void failsIfThereIsNoPayload() {
        handle(envelope.value(new GraphQLStart("operation-1", null)).build());

        verifyInvalidPayloadError();
      }

      @Test
      void failsIfThereIsNoQuery() {
        handle(envelope.value(new GraphQLStart("operation-1", new GraphQLStart.Payload(null, null, null))).build());

        verifyInvalidPayloadError();
      }

      private void verifyInvalidPayloadError() {
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(connectionsManager).sendMessage(eq(config), eq(CONNECTION_ID), message.capture());
        assertThat(message.getValue()).isEqualTo(new GraphQLErrorMessage("operation-1", new GraphQLErrorMessage.Payload(
            List.of(Map.of("message", "Message payload is invalid it must contain at least the \"query\" property")))));
        verifyNoInteractions(callbacks);
      }

      @Test
      void callsOnStartOperationWithTheRightParameters() {
        UserEnvelope user = UserEnvelope.builder().username("user@example.com").roles(List.of("User"))
            .claims(Map.of()).build();
        when(connectionsManager.fetchData(config, CONNECTION_ID))
            .thenReturn(Optional.of(new ConnectionDataEnvelope(NOW.getEpochSecond() + 60, user)));
        when(callbacks.onStartOperation(any())).thenReturn(Map.of("data", "The result"));
        GraphQLRequestEnvelope startEnvelope = envelope.build();

        handle(startEnvelope);

        verify(callbacks).onStartOperation(startEnvelope.toBuilder()
            .currentUser(user)
            .value(new GraphQLOperation("query { a }", "operation-1", "AnOperation", Map.of("aField", "value")))
            .build());
      }

      @Test
      void refusesAnExpiredConnectionAndDeletesItsDataAndSubscriptions() {
        // TypeScript reuses the stored user for as long as the socket stays open (AWS closed it after two hours)
        UserEnvelope user = UserEnvelope.builder().username("user@example.com").roles(List.of("Admin")).build();
        when(connectionsManager.fetchData(config, CONNECTION_ID))
            .thenReturn(Optional.of(new ConnectionDataEnvelope(NOW.getEpochSecond(), user)));

        handle(envelope.build());

        verify(callbacks, never()).onStartOperation(any());
        verify(callbacks).onTerminate(CONNECTION_ID);
        verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitError(
            "The connection has expired: send connection_init to authenticate it again"));
      }

      @Test
      void refusesAConnectionWhoseTokenHasExpired() {
        UserEnvelope user = UserEnvelope.builder().username("user@example.com").roles(List.of("Admin"))
            .claims(Map.of("exp", NOW.getEpochSecond())).build();
        when(connectionsManager.fetchData(config, CONNECTION_ID))
            .thenReturn(Optional.of(new ConnectionDataEnvelope(NOW.getEpochSecond() + 60, user)));

        handle(envelope.build());

        verify(callbacks, never()).onStartOperation(any());
        verify(callbacks).onTerminate(CONNECTION_ID);
        verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitError(
            "The token of the connection has expired: send connection_init with a new token"));
      }

      @Test
      void acceptsATokenThatHasNotExpiredYet() {
        UserEnvelope user = UserEnvelope.builder().username("user@example.com")
            .claims(Map.of("exp", NOW.getEpochSecond() + 1)).build();
        when(connectionsManager.fetchData(config, CONNECTION_ID))
            .thenReturn(Optional.of(new ConnectionDataEnvelope(NOW.getEpochSecond() + 1, user)));
        when(callbacks.onStartOperation(any())).thenReturn(Map.of("data", "The result"));

        handle(envelope.build());

        verify(callbacks).onStartOperation(any());
        verify(callbacks, never()).onTerminate(any());
      }

      @Test
      void runsTheOperationAnonymouslyWithoutConnectionData() {
        when(connectionsManager.fetchData(config, CONNECTION_ID)).thenReturn(Optional.empty());
        GraphQLRequestEnvelope startEnvelope = envelope.currentUser(
            UserEnvelope.builder().username("from-token").build()).build();

        handle(startEnvelope);

        ArgumentCaptor<GraphQLRequestEnvelope> started = ArgumentCaptor.forClass(GraphQLRequestEnvelope.class);
        verify(callbacks).onStartOperation(started.capture());
        assertThat(started.getValue().currentUser()).isNull();
      }

      @Test
      void doesNotSendAnythingBackForTheResultOfASubscription() {
        when(callbacks.onStartOperation(any())).thenReturn(new IteratorPublisher(List.of().iterator()));

        handle(envelope.build());

        verify(connectionsManager, never()).sendMessage(any(), any(), any());
      }

      @Test
      void treatsReactivePublishersAndIteratorsAsSubscriptionsToo() {
        org.reactivestreams.Publisher<Object> publisher = subscriber -> {};
        when(callbacks.onStartOperation(any())).thenReturn(publisher, List.of().iterator());

        handle(envelope.build());
        handle(envelope.build());

        verify(connectionsManager, never()).sendMessage(any(), any(), any());
      }

      @Test
      void sendsBackTheDataAndCompleteMessagesForAQueryOrMutation() {
        Map<String, Object> result = Map.of("data", "The result");
        when(callbacks.onStartOperation(any())).thenReturn(result);

        handle(envelope.build());

        InOrder order = inOrder(connectionsManager);
        order.verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLData("operation-1", result));
        order.verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLComplete("operation-1"));
        order.verifyNoMoreInteractions();
      }

      @Test
      void sendsAConnectionErrorWhenTheOperationFails() {
        when(callbacks.onStartOperation(any())).thenThrow(new IllegalStateException("Boom"));

        handle(envelope.build());

        verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLInitError("Boom"));
      }
    }

    @Nested
    class WithAStopMessage {

      @BeforeEach
      void setMessage() {
        envelope.value(new GraphQLStop("operation-1"));
      }

      @Test
      void failsIfThereIsNoId() {
        handle(envelope.value(new GraphQLStop(null)).build());

        verify(connectionsManager).sendMessage(config, CONNECTION_ID,
            new GraphQLInitError("Missing \"id\" in stop message"));
        verifyNoInteractions(callbacks);
      }

      @Test
      void callsOnStopOperationWithTheRightParameters() {
        handle(envelope.build());

        verify(callbacks).onStopOperation(CONNECTION_ID, "operation-1");
      }

      @Test
      void sendsBackACompleteMessage() {
        handle(envelope.build());

        InOrder order = inOrder(callbacks, connectionsManager);
        order.verify(callbacks).onStopOperation(CONNECTION_ID, "operation-1");
        order.verify(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLComplete("operation-1"));
      }

      @Test
      void ignoresAFailureToSendTheCompleteMessage() {
        RuntimeException closed = new IllegalStateException("closed");
        doThrow(closed).when(connectionsManager).sendMessage(config, CONNECTION_ID, new GraphQLComplete("operation-1"));

        handle(envelope.build());

        verify(logger).info(anyString(), eq("Received an exception while sending the \"complete\" message after "
            + "stopping the GraphQL operation with id operation-1.The client probably closed the connection. "
            + "Ignoring. "), eq(closed));
        verify(connectionsManager).sendMessage(any(), any(), any());
      }
    }

    @Test
    void callsOnTerminateAndSendsNothingBackForAConnectionTerminateMessage() {
      handle(envelope.value(new GraphQLTerminate()).build());

      verify(callbacks).onTerminate(CONNECTION_ID);
      verify(connectionsManager, never()).sendMessage(any(), any(), any());
    }

    @Test
    void failsOnlyWhenTheErrorCannotBeSent() {
      doThrow(new IllegalStateException("gone")).when(connectionsManager).sendMessage(any(), any(), any());

      assertThatThrownBy(() -> websocketHandler.handle(envelope.value(null).build()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("gone");
    }
  }
}
