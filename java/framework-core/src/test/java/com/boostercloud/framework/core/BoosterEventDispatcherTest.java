package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.same;
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
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Port of {@code test/booster-event-dispatcher.test.ts}, plus an end-to-end run of the event pipeline (provider
 * envelopes → dedup → handlers, snapshot and projection) with the real parser, processor and event store.
 */
class BoosterEventDispatcherTest extends BoosterTestSupport {

  static final List<Object> HANDLED = Collections.synchronizedList(new ArrayList<>());

  public record SomeEvent(UUID id, int amount) {
    public UUID entityID() {
      return id;
    }
  }

  public record SomeNotification(String message) {}

  public record SomeEntity(UUID id, int total) {
    public static SomeEntity reduce(SomeEvent event, SomeEntity current) {
      return new SomeEntity(event.id(), (current == null ? 0 : current.total()) + event.amount());
    }
  }

  public static final class SomeEventHandler {
    public static void handle(SomeEvent event, Register register) {
      HANDLED.add(event);
    }
  }

  public static final class SomeNotificationHandler {
    public static void handle(SomeNotification notification, Register register) {
      HANDLED.add(notification);
    }
  }

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private Logger logger;
  private final List<Object> rawEvents = List.of(Map.of("some", "raw event"), Map.of("some", "other raw event"));

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    HANDLED.clear();
    config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    config.events.put("SomeEvent", new EventMetadata(SomeEvent.class));
    config.notifications.put("SomeNotification", new NotificationMetadata(SomeNotification.class));
    logger = mock(Logger.class);
    config.logger = logger;
  }

  static EventEnvelope envelope(String entityTypeName, String entityID, String typeName, Object value, String createdAt) {
    return EventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(entityID))
        .entityTypeName(entityTypeName)
        .typeName(typeName)
        .value(value)
        .requestID(UUID.of("request-" + createdAt))
        .createdAt(createdAt)
        .build();
  }

  @Test
  void callsTheRawEventsParserOnceWithTheEnvelopesOfTheProvider() {
    List<EventEnvelope> envelopes = List.of(envelope("SomeEntity", "1", "SomeEvent", Map.of(), "t1"));
    when(events.rawToEnvelopes(rawEvents)).thenReturn(envelopes);
    try (MockedStatic<RawEventsParser> parser = mockStatic(RawEventsParser.class)) {
      BoosterEventDispatcher.dispatch(rawEvents, config);

      parser.verify(() -> RawEventsParser.streamPerEntityEvents(same(config), same(envelopes), notNull()));
    }
  }

  @Test
  void logsAndIgnoresTheErrorsOfTheRawEventsParser() {
    IllegalStateException error = new IllegalStateException("some error");
    when(events.rawToEnvelopes(rawEvents)).thenReturn(List.of());
    try (MockedStatic<RawEventsParser> parser = mockStatic(RawEventsParser.class)) {
      parser.when(() -> RawEventsParser.streamPerEntityEvents(any(), any(), any())).thenThrow(error);

      assertThatCode(() -> BoosterEventDispatcher.dispatch(rawEvents, config)).doesNotThrowAnyException();
    }
    verify(logger).error("[Booster]|BoosterEventDispatcher#dispatch: ", "Unhandled error while dispatching event: ", error);
  }

  @Test
  void logsAndIgnoresTheErrorsOfTheProviderConversion() {
    IllegalArgumentException error = new IllegalArgumentException("malformed");
    when(events.rawToEnvelopes(rawEvents)).thenThrow(error);

    assertThatCode(() -> BoosterEventDispatcher.dispatch(rawEvents, config)).doesNotThrowAnyException();

    verify(logger).error("[Booster]|BoosterEventDispatcher#dispatch: ", "Unhandled error while dispatching event: ", error);
    verify(logger).debug("[Booster]|BoosterEventDispatcher#dispatch: ", "Event workflow started for raw events:", rawEvents);
  }

  @Test
  void runsTheWholePipelinePerEntity() {
    config.entities.put("SomeEntity", new EntityMetadata(SomeEntity.class, BoosterAuthorizer.DENY_ACCESS));
    config.reducers.put("SomeEvent", new ReducerMetadata(SomeEntity.class, "reduce"));
    config.eventHandlers.put("SomeEvent", List.of(SomeEventHandler.class));
    config.eventHandlers.put("SomeNotification", List.of(SomeNotificationHandler.class));
    config.topicToEvent.put("defaultTopic", "SomeNotification");
    EventEnvelope first = envelope("SomeEntity", "1", "SomeEvent", Map.of("id", "1", "amount", 2), "2026-11-10T10:00:00.000Z");
    EventEnvelope second = envelope("SomeEntity", "1", "SomeEvent", Map.of("id", "1", "amount", 3), "2026-11-10T10:00:01.000Z");
    EventEnvelope duplicated = envelope("SomeEntity", "2", "SomeEvent", Map.of("id", "2", "amount", 5), "2026-11-10T10:00:02.000Z");
    EventEnvelope notification = envelope("defaultTopic", "default", "SomeNotification", Map.of("message", "hi"),
        "2026-11-10T10:00:03.000Z");
    when(events.rawToEnvelopes(rawEvents)).thenReturn(List.of(first, duplicated, second, notification));
    when(events.storeDispatched(any(), eq(config))).thenAnswer(invocation -> invocation.getArgument(0) != duplicated);
    when(events.latestEntitySnapshot(any(), any(), any())).thenReturn(Optional.empty());
    when(events.forEntitySince(config, "SomeEntity", UUID.of("1"), EventStore.ORIGIN_OF_TIME)).thenReturn(List.of(first, second));
    when(events.storeSnapshot(any(), eq(config))).thenAnswer(invocation ->
        invocation.<NonPersistedEntitySnapshotEnvelope>getArgument(0).persisted("2026-11-10T10:00:05.000Z"));
    ReadModelStore readModelStore = mock(ReadModelStore.class);

    BoosterEventDispatcher.dispatch(rawEvents, config, new EventStore(config), readModelStore);

    assertThat(HANDLED).containsExactlyInAnyOrder(new SomeEvent(UUID.of("1"), 2), new SomeEvent(UUID.of("1"), 3),
        new SomeNotification("hi"));
    EntitySnapshotEnvelope expectedSnapshot = NonPersistedEntitySnapshotEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(second.requestID()).entityID(UUID.of("1"))
        .entityTypeName("SomeEntity").typeName("SomeEntity").value(new SomeEntity(UUID.of("1"), 5))
        .snapshottedEventCreatedAt("2026-11-10T10:00:01.000Z").build().persisted("2026-11-10T10:00:05.000Z");
    verify(readModelStore).project(expectedSnapshot);
    // the duplicated event is neither handled nor reduced, and notifications are never reduced
    verify(events, never()).forEntitySince(any(), eq("SomeEntity"), eq(UUID.of("2")), any());
    verify(events, never()).forEntitySince(any(), eq("defaultTopic"), any(), any());
    verify(logger).warn("[Booster]|BoosterEventDispatcher#filterDispatched: ",
        "Event has already been dispatched. Skipping.", duplicated);
  }

  @Test
  void isTraced() {
    List<String> traced = Collections.synchronizedList(new ArrayList<>());
    Booster.config().traceConfiguration.enableFor(TraceActionTypes.DISPATCH_EVENTS.value());
    Booster.config().traceConfiguration.onStart = (traceConfig, actionType, info) ->
        traced.add(actionType + " " + info.className + "#" + info.methodName + " " + info.args.size());
    when(events.rawToEnvelopes(rawEvents)).thenReturn(List.of());

    BoosterEventDispatcher.dispatch(rawEvents, config);

    assertThat(traced).containsExactly("DISPATCH_EVENTS BoosterEventDispatcher#dispatch 2");
  }
}
