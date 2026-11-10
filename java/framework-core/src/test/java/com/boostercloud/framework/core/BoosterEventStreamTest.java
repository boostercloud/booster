package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.RawEventsParser;
import com.boostercloud.framework.core.services.ReadModelStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

/**
 * Tests of {@link BoosterEventStreamConsumer} and {@link BoosterEventStreamProducer}, which TypeScript only exercises
 * in integration tests (spec 06 §5.9): provider argument order, the shared pipeline, and errors logged, never thrown.
 */
class BoosterEventStreamTest extends BoosterTestSupport {

  static final List<Object> HANDLED = Collections.synchronizedList(new ArrayList<>());

  public record StreamedEvent(UUID id) {
    public UUID entityID() {
      return id;
    }
  }

  public static final class StreamedEventHandler {
    public static void handle(StreamedEvent event, Register register) {
      HANDLED.add(event);
    }
  }

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private Logger logger;
  private final Object rawEvents = Map.of("messages", List.of("m1", "m2"));

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    HANDLED.clear();
    config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    logger = mock(Logger.class);
    config.logger = logger;
  }

  static EventEnvelope envelope(String entityTypeName, String entityID, String deletedAt) {
    return EventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(entityID))
        .entityTypeName(entityTypeName)
        .typeName("StreamedEvent")
        .value(Map.of("id", entityID))
        .requestID(UUID.generate())
        .createdAt("2026-11-10T10:00:00.000Z")
        .deletedAt(deletedAt)
        .build();
  }

  @Nested
  class Consumer {

    @Test
    void dedupsConvertsAndStreamsTheEventsPerEntity() {
      List<Object> dedupEvents = List.of("m1");
      List<EventEnvelope> envelopes = List.of(envelope("SomeEntity", "1", null));
      when(events.dedupEventStream(config, rawEvents)).thenReturn(dedupEvents);
      when(events.rawStreamToEnvelopes(config, rawEvents, dedupEvents)).thenReturn(envelopes);
      try (MockedStatic<RawEventsParser> parser = mockStatic(RawEventsParser.class)) {
        BoosterEventStreamConsumer.consume(rawEvents, config);

        parser.verify(() -> RawEventsParser.streamPerEntityEvents(same(config), same(envelopes), notNull()));
      }
      InOrder order = inOrder(events);
      order.verify(events).dedupEventStream(config, rawEvents);
      order.verify(events).rawStreamToEnvelopes(config, rawEvents, dedupEvents);
    }

    @Test
    void runsTheEventHandlersOfTheFirstSeenEvents() {
      config.events.put("StreamedEvent", new EventMetadata(StreamedEvent.class));
      config.eventHandlers.put("StreamedEvent", List.of(StreamedEventHandler.class));
      config.topicToEvent.put("SomeTopic", "StreamedEvent");
      EventEnvelope envelope = envelope("SomeTopic", "1", null);
      when(events.dedupEventStream(config, rawEvents)).thenReturn(List.of("m1"));
      when(events.rawStreamToEnvelopes(config, rawEvents, List.of("m1"))).thenReturn(List.of(envelope));
      EventStore eventStore = mock(EventStore.class);
      when(eventStore.storeDispatchedEvent(envelope)).thenReturn(true);

      BoosterEventStreamConsumer.consume(rawEvents, config, eventStore, mock(ReadModelStore.class));

      assertThat(HANDLED).containsExactly(new StreamedEvent(UUID.of("1")));
      verify(eventStore).storeDispatchedEvent(envelope);
      verify(eventStore, never()).fetchEntitySnapshot(any(), any());
    }

    @Test
    void logsAndIgnoresErrors() {
      IllegalStateException error = new IllegalStateException("dedup store down");
      when(events.dedupEventStream(config, rawEvents)).thenThrow(error);

      assertThatCode(() -> BoosterEventStreamConsumer.consume(rawEvents, config)).doesNotThrowAnyException();

      verify(logger).error("[Booster]|BoosterEventStreamConsumer#consume: ", "Unhandled error while consuming event: ",
          error);
      verify(logger).debug("[Booster]|BoosterEventStreamConsumer#consume: ",
          "Stream event workflow started for raw events:", rawEvents);
    }

    @Test
    void isTraced() {
      List<String> traced = Collections.synchronizedList(new ArrayList<>());
      Booster.config().traceConfiguration.enableFor(TraceActionTypes.CONSUME_STREAM_EVENTS.value());
      Booster.config().traceConfiguration.onStart = (traceConfig, actionType, info) ->
          traced.add(actionType + " " + info.className + "#" + info.methodName);
      when(events.dedupEventStream(config, rawEvents)).thenReturn(List.of());
      when(events.rawStreamToEnvelopes(config, rawEvents, List.of())).thenReturn(List.of());

      BoosterEventStreamConsumer.consume(rawEvents, config);

      assertThat(traced).containsExactly("CONSUME_STREAM_EVENTS BoosterEventStreamConsumer#consume");
    }
  }

  @Nested
  class Producer {

    @Test
    void producesTheEventsOfEachEntityWithTheProvider() {
      EventEnvelope a1 = envelope("EntityA", "A", null);
      EventEnvelope b1 = envelope("EntityB", "B", null);
      EventEnvelope a2 = envelope("EntityA", "A", null);
      EventEnvelope deleted = envelope("EntityA", "A", "2026-11-11T00:00:00.000Z");
      when(events.rawToEnvelopes(rawEvents)).thenReturn(List.of(a1, b1, deleted, a2));

      BoosterEventStreamProducer.produce(rawEvents, config);

      verify(events).produce("EntityA", UUID.of("A"), List.of(a1, a2), config);
      verify(events).produce("EntityB", UUID.of("B"), List.of(b1), config);
    }

    @Test
    void keepsProducingTheOtherEntitiesWhenOneFails() {
      EventEnvelope a1 = envelope("EntityA", "A", null);
      EventEnvelope b1 = envelope("EntityB", "B", null);
      when(events.rawToEnvelopes(rawEvents)).thenReturn(List.of(a1, b1));
      IllegalStateException error = new IllegalStateException("stream unavailable");
      doThrow(error).when(events).produce("EntityA", UUID.of("A"), List.of(a1), config);

      assertThatCode(() -> BoosterEventStreamProducer.produce(rawEvents, config)).doesNotThrowAnyException();

      verify(events).produce("EntityB", UUID.of("B"), List.of(b1), config);
      verify(logger).error("[Booster]|RawEventsParser#streamPerEntityEvents: ",
          "An error occurred while processing events for entity EntityA with ID A", error);
    }

    @Test
    void logsAndIgnoresErrors() {
      IllegalArgumentException error = new IllegalArgumentException("malformed request");
      when(events.rawToEnvelopes(rawEvents)).thenThrow(error);

      assertThatCode(() -> BoosterEventStreamProducer.produce(rawEvents, config)).doesNotThrowAnyException();

      verify(logger).error("[Booster]|BoosterEventStreamProducer#produce: ", "Unhandled error while producing events: ",
          error);
      verify(logger).debug("[Booster]|BoosterEventStreamProducer#produce: ",
          "Produce event workflow started for request:", rawEvents);
    }

    @Test
    void isTraced() {
      List<String> traced = Collections.synchronizedList(new ArrayList<>());
      Booster.config().traceConfiguration.enableFor(TraceActionTypes.PRODUCE_STREAM_EVENTS.value());
      Booster.config().traceConfiguration.onStart = (traceConfig, actionType, info) ->
          traced.add(actionType + " " + info.className + "#" + info.methodName);
      when(events.rawToEnvelopes(rawEvents)).thenReturn(List.of());

      BoosterEventStreamProducer.produce(rawEvents, config);

      assertThat(traced).containsExactly("PRODUCE_STREAM_EVENTS BoosterEventStreamProducer#produce");
    }
  }
}
