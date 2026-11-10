package com.boostercloud.framework.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventStoreEntryEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/services/raw-events-parser.test.ts}, plus filtering, concurrency and key edge cases. */
class RawEventsParserTest {

  record Call(String entityName, UUID entityID, List<EventEnvelope> eventEnvelopes, BoosterConfig config) {}

  private BoosterConfig config;
  private Logger logger;
  private List<Call> calls;
  private EventEnvelope entityAEvent1;
  private EventEnvelope entityAEvent2;
  private EventEnvelope entityAEvent3;
  private EventEnvelope entityBEvent1;
  private EventEnvelope entityBEvent2;
  private EventEnvelope entityBEvent3;
  private EventEnvelope entityBEvent4;
  private List<EventStoreEntryEnvelope> events;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    calls = Collections.synchronizedList(new ArrayList<>());
    entityAEvent1 = event("EntityA", "EntityAID", "A1", null);
    entityAEvent2 = event("EntityA", "EntityAID", "A2", null);
    entityAEvent3 = event("EntityA", "EntityAID", "A3", null);
    entityBEvent1 = event("EntityB", "EntityBID", "B1", null);
    entityBEvent2 = event("EntityB", "EntityBID", "B2", null);
    entityBEvent3 = event("EntityB", "EntityBID", "B3", null);
    entityBEvent4 = event("EntityB", "EntityBID", "B4", null);
    events = List.of(entityAEvent1, snapshot(), entityBEvent1, snapshot(), entityAEvent2, entityBEvent2, snapshot(),
        snapshot(), entityBEvent3, entityAEvent3, snapshot(), entityBEvent4, snapshot());
  }

  static EventEnvelope event(String entityTypeName, String entityID, String value, String deletedAt) {
    return EventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(entityID))
        .entityTypeName(entityTypeName)
        .value(Map.of("value", value))
        .requestID(UUID.generate())
        .typeName("SomeEvent")
        .createdAt("2026-11-10T10:00:00.000Z")
        .deletedAt(deletedAt)
        .build();
  }

  static NonPersistedEntitySnapshotEnvelope snapshot() {
    return NonPersistedEntitySnapshotEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.generate())
        .entityTypeName("SnapshottedEntity")
        .typeName("SnapshottedEntity")
        .value(Map.of())
        .requestID(UUID.generate())
        .snapshottedEventCreatedAt("2026-11-10T10:00:00.000Z")
        .build();
  }

  private RawEventsParser.EventsStreamingCallback recording() {
    return (entityName, entityID, eventEnvelopes, callbackConfig) ->
        calls.add(new Call(entityName, entityID, eventEnvelopes, callbackConfig));
  }

  @Test
  void stripsAllTheSnapshotsAndDoesNotCallTheCallbackForThem() {
    RawEventsParser.streamPerEntityEvents(config, events, recording());

    assertThat(calls).extracting(Call::entityName).doesNotContain("SnapshottedEntity");
  }

  @Test
  void callsTheCallbackOncePerEntityWithItsEventsInOrder() {
    RawEventsParser.streamPerEntityEvents(config, events, recording());

    assertThat(calls).containsExactlyInAnyOrder(
        new Call("EntityA", UUID.of("EntityAID"), List.of(entityAEvent1, entityAEvent2, entityAEvent3), config),
        new Call("EntityB", UUID.of("EntityBID"), List.of(entityBEvent1, entityBEvent2, entityBEvent3, entityBEvent4),
            config));
    assertThat(calls).allSatisfy(call -> assertThat(call.config()).isSameAs(config));
  }

  @Test
  void keepsProcessingTheOtherEntitiesAndLogsWhenACallbackFails() {
    IllegalStateException error = new IllegalStateException("Testing error");
    RawEventsParser.EventsStreamingCallback callback = (entityName, entityID, eventEnvelopes, callbackConfig) -> {
      calls.add(new Call(entityName, entityID, eventEnvelopes, callbackConfig));
      if (entityName.equals("EntityA")) {
        throw error;
      }
    };

    assertThatCode(() -> RawEventsParser.streamPerEntityEvents(config, events, callback)).doesNotThrowAnyException();

    assertThat(calls).extracting(Call::entityName).containsExactlyInAnyOrder("EntityA", "EntityB");
    assertThat(calls).filteredOn(call -> call.entityName().equals("EntityB")).singleElement()
        .extracting(Call::eventEnvelopes).isEqualTo(List.of(entityBEvent1, entityBEvent2, entityBEvent3, entityBEvent4));
    verify(logger).error("[Booster]|RawEventsParser#streamPerEntityEvents: ",
        "An error occurred while processing events for entity EntityA with ID EntityAID", error);
  }

  @Test
  void dropsSoftDeletedEventsButKeepsAnEmptyDeletedAt() {
    EventEnvelope deleted = event("EntityA", "EntityAID", "deleted", "2026-11-11T08:00:00.000Z");
    EventEnvelope emptyDeletedAt = event("EntityA", "EntityAID", "kept", "");

    RawEventsParser.streamPerEntityEvents(config, List.of(deleted, emptyDeletedAt, entityAEvent1), recording());

    assertThat(calls).singleElement().extracting(Call::eventEnvelopes).isEqualTo(List.of(emptyDeletedAt, entityAEvent1));
  }

  @Test
  void doesNothingWithoutEvents() {
    RawEventsParser.streamPerEntityEvents(config, List.of(), recording());
    RawEventsParser.streamPerEntityEvents(config, List.of(snapshot()), recording());

    assertThat(calls).isEmpty();
  }

  @Test
  void groupsByEntityTypeAndIdWithoutConcatenationCollisions() {
    // TypeScript's `${entityTypeName}-${entityID}` key would merge these two
    EventEnvelope first = event("A-B", "C", "1", null);
    EventEnvelope second = event("A", "B-C", "2", null);

    RawEventsParser.streamPerEntityEvents(config, List.of(first, second), recording());

    assertThat(calls).hasSize(2);
  }

  @Test
  void processesTheEntitiesConcurrentlyAndReturnsWhenAllFinished() {
    CountDownLatch bothStarted = new CountDownLatch(2);
    List<String> finished = Collections.synchronizedList(new ArrayList<>());
    RawEventsParser.EventsStreamingCallback callback = (entityName, entityID, eventEnvelopes, callbackConfig) -> {
      bothStarted.countDown();
      try {
        // Only completes if the other entity runs at the same time
        assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      finished.add(entityName);
    };

    RawEventsParser.streamPerEntityEvents(config, events, callback);

    assertThat(finished).containsExactlyInAnyOrder("EntityA", "EntityB");
  }

  @Test
  void logsTheEventsOfEachEntityAtDebugLevel() {
    RawEventsParser.streamPerEntityEvents(config, List.of(entityAEvent1), recording());

    verify(logger).debug("[Booster]|RawEventsParser#streamPerEntityEvents: ",
        "Streaming the following events for entity 'EntityA' and ID 'EntityAID':", List.of(entityAEvent1));
  }
}
