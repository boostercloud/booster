package com.boostercloud.framework.provider.local.services;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/event-registry.test.ts} (on a temporary {@code .booster/events.json}). */
class EventRegistryTest {

  @TempDir
  Path project;

  private int initialEventsCount;
  private EventRegistry eventRegistry;

  @BeforeEach
  void setUp() {
    initialEventsCount = ThreadLocalRandom.current().nextInt(2, 11);
    eventRegistry = new EventRegistry(project.resolve(".booster/events.json"));
    eventRegistry.deleteAll();
  }

  @AfterEach
  void tearDown() {
    eventRegistry.events().close();
  }

  private static Map<String, Object> raw(Object envelope) {
    return BoosterJson.toRawMap(envelope);
  }

  /** Stores concurrently, like the TypeScript test's {@code Promise.all}. */
  private void storeAll(List<EventEnvelope> events) throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (EventEnvelope event : events) {
        executor.submit(() -> eventRegistry.store(raw(event)));
      }
    }
  }

  @Nested
  class StoreEventBatch {
    @AfterEach
    void resetClock() {
      com.boostercloud.framework.types.Dates.resetClock();
    }

    private void useClock(String instant) {
      com.boostercloud.framework.types.Dates.useClock(
          java.time.Clock.fixed(Instant.parse(instant), java.time.ZoneOffset.UTC));
    }

    @Test
    void givesStrictlyIncreasingCreatedAtsEvenWithinOneMillisecond() {
      useClock("2026-11-10T09:15:27.412Z");
      Map<String, Object> first = raw(Mocks.event());
      Map<String, Object> second = raw(Mocks.event());
      assertThat(eventRegistry.storeEventBatch(List.of(first, second)))
          .containsExactly("2026-11-10T09:15:27.412Z", "2026-11-10T09:15:27.413Z");
      assertThat(first).containsEntry("createdAt", "2026-11-10T09:15:27.412Z");
      // a clock that goes back does not take createdAt back either
      useClock("2026-11-10T09:00:00.000Z");
      assertThat(eventRegistry.storeEventBatch(List.of(raw(Mocks.event()))))
          .containsExactly("2026-11-10T09:15:27.414Z");
      useClock("2026-11-10T10:00:00.000Z");
      assertThat(eventRegistry.storeEventBatch(List.of(raw(Mocks.event()))))
          .containsExactly("2026-11-10T10:00:00.000Z");
      assertThat(eventRegistry.query(map("kind", "event"))).extracting(e -> e.get("createdAt")).containsExactly(
          "2026-11-10T09:15:27.412Z", "2026-11-10T09:15:27.413Z", "2026-11-10T09:15:27.414Z",
          "2026-11-10T10:00:00.000Z");
      assertThat(eventRegistry.storeEventBatch(List.of())).isEmpty();
    }

    @Test
    void startsAfterTheLatestStoredCreatedAt() {
      Map<String, Object> stored = raw(Mocks.event());
      stored.put("createdAt", "2026-11-10T09:15:27.412Z");
      eventRegistry.store(stored);
      eventRegistry.events().close();
      eventRegistry = new EventRegistry(project.resolve(".booster/events.json"));
      useClock("2026-11-10T09:15:27.000Z");
      assertThat(eventRegistry.storeEventBatch(List.of(raw(Mocks.event()))))
          .containsExactly("2026-11-10T09:15:27.413Z");
    }
  }

  @Nested
  class Query {
    @Test
    void withADbFullOfRandomEventsShouldReturnTheExpectedEvent() throws Exception {
      List<EventEnvelope> events = new ArrayList<>();
      for (int i = 0; i < initialEventsCount; i++) {
        events.add(Mocks.event());
      }
      storeAll(events);
      EventEnvelope mockTargetEvent = Mocks.event();
      eventRegistry.store(raw(mockTargetEvent));
      Map<String, Object> target = raw(mockTargetEvent);
      List<Map<String, Object>> result = eventRegistry.query(map(
          "kind", target.get("kind"),
          "entityID", target.get("entityID"),
          "entityTypeName", target.get("entityTypeName"),
          "value", target.get("value"),
          "createdAt", target.get("createdAt"),
          "requestID", target.get("requestID"),
          "typeName", target.get("typeName"),
          "version", target.get("version")));
      assertThat(result).hasSize(1);
      assertThat(result.get(0)).containsAllEntriesOf(target);
    }

    @Test
    void withEventsOfTheSameEntityShouldReturnThemSorted() throws Exception {
      String entityName = Mocks.word();
      String entityId = Mocks.uuid();
      List<EventEnvelope> events = new ArrayList<>();
      for (int i = 0; i < initialEventsCount; i++) {
        events.add(Mocks.eventForEntity(entityName, entityId));
      }
      for (int i = 0; i < initialEventsCount; i++) {
        events.add(Mocks.eventForEntity(entityName, Mocks.uuid()));
      }
      for (int i = 0; i < initialEventsCount; i++) {
        events.add(Mocks.event());
      }
      storeAll(events);
      List<Map<String, Object>> result =
          eventRegistry.query(map("kind", "event", "entityID", entityId, "entityTypeName", entityName));
      assertThat(result).hasSize(initialEventsCount);
      assertThat(result.get(0)).containsEntry("entityID", entityId).containsEntry("entityTypeName", entityName);
      Instant first = Instant.parse((String) result.get(0).get("createdAt"));
      Instant last = Instant.parse((String) result.get(result.size() - 1).get("createdAt"));
      assertThat(first).isBefore(last);
      for (int i = 1; i < result.size(); i++) {
        assertThat((String) result.get(i - 1).get("createdAt")).isLessThanOrEqualTo((String) result.get(i).get("createdAt"));
      }
    }

    @Test
    void sortsNewestFirstLimitsAndProjects() throws Exception {
      String entityName = Mocks.word();
      List<EventEnvelope> events = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        events.add(Mocks.eventForEntity(entityName, "e" + i));
      }
      storeAll(events);
      List<Map<String, Object>> newest = eventRegistry.query(map("entityTypeName", entityName), -1, 2,
          map("entityID", 1));
      assertThat(newest).hasSize(2);
      assertThat(newest.get(0).keySet()).containsExactlyInAnyOrder("entityID", "_id");
      String latest = events.stream().map(EventEnvelope::createdAt).max(String::compareTo).orElseThrow();
      String latestId = events.stream().filter(e -> e.createdAt().equals(latest)).findFirst().orElseThrow()
          .entityID().value();
      assertThat(newest.get(0).get("entityID")).isEqualTo(latestId);
      assertThat(eventRegistry.query(map("entityTypeName", entityName), 1, 0, null)).hasSize(5);
    }
  }

  @Nested
  class QueryLatestEntitySnapshot {
    private EntitySnapshotEnvelope mockTargetSnapshot;
    private EntitySnapshotEnvelope copyOfMockTargetSnapshot;

    @BeforeEach
    void setUp() {
      mockTargetSnapshot = Mocks.snapshot(null, null);
      eventRegistry.store(raw(mockTargetSnapshot));
      copyOfMockTargetSnapshot = mockTargetSnapshot.toBuilder()
          .snapshottedEventCreatedAt(com.boostercloud.framework.types.Dates.nowIso()).build();
      eventRegistry.store(raw(copyOfMockTargetSnapshot));
    }

    @Test
    void shouldReturnTheLatestItem() {
      Map<String, Object> result = eventRegistry.queryLatestSnapshot(
          map("entityID", mockTargetSnapshot.entityID().value(), "entityTypeName", mockTargetSnapshot.entityTypeName()));
      assertThat(result).isNotNull();
      Map<String, Object> rest = new java.util.LinkedHashMap<>(result);
      rest.remove("_id");
      assertThat(rest).isEqualTo(raw(copyOfMockTargetSnapshot));
    }

    @Test
    void shouldReturnNothingForAnotherEntity() {
      assertThat(eventRegistry.queryLatestSnapshot(map("entityID", Mocks.uuid(), "entityTypeName",
          mockTargetSnapshot.entityTypeName()))).isNull();
    }
  }

  @Nested
  class DeleteAll {
    @Test
    void shouldClearAllEvents() {
      eventRegistry.store(raw(Mocks.event()));
      assertThat(eventRegistry.deleteAll()).isEqualTo(1);
      assertThat(eventRegistry.query(map())).isEmpty();
    }
  }

  @Nested
  class ThePublishMethod {
    @Test
    void shouldInsertEventsIntoTheEventsDatabase() {
      Datastore datastore = mock(Datastore.class);
      EventRegistry registry = new EventRegistry(datastore);
      Map<String, Object> mockEvent = raw(Mocks.event());
      registry.store(mockEvent);
      verify(datastore).insert(mockEvent);
    }

    @Test
    void shouldThrowIfTheDatabaseInsertFails() {
      Datastore datastore = mock(Datastore.class);
      EventRegistry registry = new EventRegistry(datastore);
      IllegalStateException error = new IllegalStateException(Mocks.word());
      when(datastore.insert(any(Map.class))).thenThrow(error);
      assertThatThrownBy(() -> registry.store(raw(Mocks.event()))).isSameAs(error);
    }
  }

  @Nested
  class ReplaceOrDeleteItem {
    @Test
    void replacesOrRemovesByDatabaseId() {
      eventRegistry.store(map("_id", "x", "kind", "event", "value", map("a", 1)));
      eventRegistry.replaceOrDeleteItem("x", map("kind", "event", "value", map(), "deletedAt", "now"));
      assertThat(eventRegistry.query(map("_id", "x"))).containsExactly(map("kind", "event", "value", map(),
          "deletedAt", "now", "_id", "x"));
      eventRegistry.replaceOrDeleteItem("x");
      assertThat(eventRegistry.count()).isZero();
    }
  }
}
