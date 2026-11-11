package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.provider.local.services.DispatchJournal;
import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/library/events-adapter.test.ts}. */
class EventsAdapterTest {

  private BoosterConfig mockConfig;
  private NonPersistedEventEnvelope mockEventEnvelop;
  private EntitySnapshotEnvelope mockSnapshot;
  private Logger logger;
  private UserApp mockUserApp;
  private EventRegistry mockEventRegistry;

  @BeforeEach
  void setUp() {
    mockConfig = new BoosterConfig("test");
    mockConfig.appName = "nuke-button";
    mockEventEnvelop = Mocks.nonPersistedEvent();
    mockSnapshot = Mocks.snapshot(null, null);
    logger = mock(Logger.class);
    mockConfig.logger = logger;
    mockUserApp = mock(UserApp.class);
    mockEventRegistry = mock(EventRegistry.class);
  }

  @AfterEach
  void tearDown() {
    Dates.resetClock();
  }

  private static Map<String, Object> raw(Object envelope) {
    return BoosterJson.toRawMap(envelope);
  }

  @Nested
  class RawEventsToEnvelopes {
    @Test
    void shouldReturnAnEmptyArrayOfEnvelopes() {
      assertThat(EventsAdapter.rawEventsToEnvelopes(List.of())).isEmpty();
    }

    @Test
    void shouldReturnAnArrayOfEnvelopes() {
      EventEnvelope event1 = Mocks.event();
      EventEnvelope event2 = Mocks.event();
      List<EventEnvelope> results = EventsAdapter.rawEventsToEnvelopes(List.of(event1, event2));
      assertThat(results).containsExactly(event1, event2);
      assertThat(results.get(0)).isSameAs(event1);
    }

    @Test
    void convertsRawDocumentsAndNonPersistedEnvelopes() {
      EventEnvelope event = Mocks.event();
      Map<String, Object> document = raw(event);
      document.put("_id", "dbId");
      assertThat(EventsAdapter.rawEventsToEnvelopes(List.of(document, mockEventEnvelop)))
          .containsExactly(BoosterJson.convert(raw(event), EventEnvelope.class).toBuilder().id("dbId").build(),
              mockEventEnvelop.persisted(null));
    }
  }

  @Nested
  class ReadEntityEventsSince {
    private String mockEntityTypeName;
    private UUID mockEntityID;
    private Map<String, Object> storedEvent;

    @BeforeEach
    void setUp() {
      storedEvent = raw(mockEventEnvelop.persisted("2026-01-01T00:00:00.000Z"));
      storedEvent.put("_id", "abc");
      when(mockEventRegistry.query(any())).thenReturn(List.of(storedEvent));
      mockEntityTypeName = Mocks.word();
      mockEntityID = UUID.of(Mocks.uuid());
    }

    private Map<String, Object> expectedQuery(String since) {
      Map<String, Object> query = new LinkedHashMap<>();
      query.put("createdAt", map("$gt", since));
      query.put("kind", "event");
      query.put("entityID", mockEntityID.value());
      query.put("entityTypeName", mockEntityTypeName);
      query.put("deletedAt", map("$exists", false));
      return query;
    }

    @Test
    void shouldReturnExpectedResult() {
      List<EventEnvelope> result = EventsAdapter.readEntityEventsSince(mockEventRegistry, mockConfig,
          mockEntityTypeName, mockEntityID, null);
      List<EventEnvelope> expected =
          List.of(BoosterJson.convert(storedEvent, EventEnvelope.class).toBuilder().id("abc").build());
      assertThat(result).isEqualTo(expected);
      assertThat(result.get(0).id()).isEqualTo("abc");
      verify(logger).debug("[Booster]|events-adapter#readEntityEventsSince: ",
          "Loaded events for entity " + mockEntityTypeName + " with ID " + mockEntityID + " with result:", expected);
    }

    @Test
    void dateProvidedShouldCallEventRegistryQueryWithExpectedInput() {
      String dateStr = Mocks.pastDate();
      EventsAdapter.readEntityEventsSince(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID, dateStr);
      verify(mockEventRegistry, times(1)).query(expectedQuery(dateStr));
      verify(logger).debug(org.mockito.ArgumentMatchers.eq("[Booster]|events-adapter#readEntityEventsSince: "),
          org.mockito.ArgumentMatchers.eq("Loaded events for entity " + mockEntityTypeName + " with ID " + mockEntityID
              + " with result:"), any());
    }

    @Test
    void dateNotProvidedShouldQueryFromTheOriginOfTime() {
      EventsAdapter.readEntityEventsSince(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID, null);
      verify(mockEventRegistry, times(1)).query(expectedQuery("1970-01-01T00:00:00.000Z"));
    }
  }

  @Nested
  class ReadEntityLatestSnapshot {
    private String mockEntityTypeName;
    private UUID mockEntityID;

    @BeforeEach
    void setUp() {
      when(mockEventRegistry.queryLatestSnapshot(any())).thenReturn(raw(mockSnapshot));
      mockEntityTypeName = Mocks.word();
      mockEntityID = UUID.of(Mocks.uuid());
    }

    @Test
    void shouldCallEventRegistryQueryLatest() {
      EventsAdapter.readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
      verify(mockEventRegistry, times(1)).queryLatestSnapshot(
          map("entityID", mockEntityID.value(), "entityTypeName", mockEntityTypeName, "kind", "snapshot"));
    }

    @Test
    void withSnapshotShouldLogTheExpectedMessageAndReturnIt() {
      assertThat(EventsAdapter.readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID))
          .contains(mockSnapshot);
      verify(logger, times(1)).debug("[Booster]|events-adapter#readEntityLatestSnapshot: ",
          "Snapshot found for entity " + mockEntityTypeName + " with ID " + mockEntityID + ":", mockSnapshot);
      verifyNoMoreInteractions(logger);
    }

    @Test
    void withoutSnapshotShouldLogTheExpectedMessageAndReturnNothing() {
      when(mockEventRegistry.queryLatestSnapshot(any())).thenReturn(null);
      assertThat(EventsAdapter.readEntityLatestSnapshot(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID))
          .isEmpty();
      verify(logger, times(1)).debug("[Booster]|events-adapter#readEntityLatestSnapshot: ",
          "No snapshot found for entity " + mockEntityTypeName + " with ID " + mockEntityID + ".");
      verifyNoMoreInteractions(logger);
    }
  }

  @Nested
  class StoreEvents {
    @Test
    void noEventEnvelopesShouldNotStoreButShouldDispatch() {
      EventsAdapter.storeEvents(mockUserApp, mockEventRegistry, List.of(), mockConfig);
      verify(mockEventRegistry, never()).store(any());
      verify(mockUserApp, times(1)).boosterEventDispatcher(List.of());
    }

    @Test
    void withEventEnvelopesShouldStoreThemWithTheirCreationTime() {
      Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:27.412Z"), ZoneOffset.UTC));
      EventsAdapter.storeEvents(mockUserApp, mockEventRegistry, List.of(mockEventEnvelop), mockConfig);
      Map<String, Object> expected = raw(mockEventEnvelop);
      expected.put("createdAt", "2026-11-10T09:15:27.412Z");
      verify(mockEventRegistry).store(expected);
    }

    @Test
    void withEventEnvelopesShouldCallTheDispatcherWithTheStoredEvents() {
      Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:27.412Z"), ZoneOffset.UTC));
      List<EventEnvelope> stored =
          EventsAdapter.storeEvents(mockUserApp, mockEventRegistry, List.of(mockEventEnvelop), mockConfig);
      verify(mockUserApp, times(1)).boosterEventDispatcher(List.of(mockEventEnvelop.persisted("2026-11-10T09:15:27.412Z")));
      assertThat(stored).containsExactly(mockEventEnvelop.persisted("2026-11-10T09:15:27.412Z"));
    }

    @Test
    void aFailedStoreStopsTheBatchAndDispatchesNothing() {
      org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("disk full"))
          .when(mockEventRegistry).store(any());
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> EventsAdapter.storeEvents(mockUserApp,
          mockEventRegistry, List.of(Mocks.nonPersistedEvent(), Mocks.nonPersistedEvent(), Mocks.nonPersistedEvent()),
          mockConfig)).hasMessage("disk full");
      verify(mockEventRegistry, times(2)).store(any());
      verify(mockUserApp, never()).boosterEventDispatcher(any());
    }

    @Test
    void withTheJournalTheEventsGetTheirIdsAndTheBatchIsTracked() {
      DispatchJournal journal = mock(DispatchJournal.class);
      when(mockEventRegistry.createNewId()).thenReturn("id1", "id2");
      // the registry gives the createdAt values (under its write lock)
      when(mockEventRegistry.storeEventBatch(any()))
          .thenReturn(List.of("2026-11-10T09:15:27.412Z", "2026-11-10T09:15:27.413Z"));
      Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:27.412Z"), ZoneOffset.UTC));
      NonPersistedEventEnvelope second = Mocks.nonPersistedEvent();
      List<EventEnvelope> stored = EventsAdapter.storeEvents(mockUserApp, mockEventRegistry, journal,
          List.of(mockEventEnvelop, second), mockConfig);
      org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(journal, mockEventRegistry, mockUserApp);
      inOrder.verify(journal).addPending(List.of(
          map("_id", "id1", "entityTypeName", mockEventEnvelop.entityTypeName(), "entityID",
              mockEventEnvelop.entityID().value()),
          map("_id", "id2", "entityTypeName", second.entityTypeName(), "entityID", second.entityID().value())));
      Map<String, Object> firstDocument = raw(mockEventEnvelop.persisted("2026-11-10T09:15:27.412Z"));
      firstDocument.put("_id", "id1");
      Map<String, Object> secondDocument = raw(second.persisted("2026-11-10T09:15:27.412Z"));
      secondDocument.put("_id", "id2");
      inOrder.verify(mockEventRegistry).storeEventBatch(List.of(firstDocument, secondDocument));
      inOrder.verify(mockUserApp).boosterEventDispatcher(stored);
      inOrder.verify(journal).remove(List.of("id1", "id2"));
      verify(mockEventRegistry, never()).store(any());
      assertThat(stored).extracting(EventEnvelope::id).containsExactly("id1", "id2");
      assertThat(stored).extracting(EventEnvelope::createdAt)
          .containsExactly("2026-11-10T09:15:27.412Z", "2026-11-10T09:15:27.413Z");
      assertThat(stored.get(1)).isEqualTo(second.persisted("2026-11-10T09:15:27.413Z").toBuilder().id("id2").build());
    }

    /** A store whose inserts pause the thread named {@code "A"} before they take the write lock. */
    private static final class PausingDatastore extends com.boostercloud.framework.provider.local.store.Datastore {
      final java.util.concurrent.CountDownLatch paused = new java.util.concurrent.CountDownLatch(1);
      final java.util.concurrent.CountDownLatch resume = new java.util.concurrent.CountDownLatch(1);

      PausingDatastore(Path file) {
        super(com.boostercloud.framework.provider.local.store.DatastoreOptions.of(file));
      }

      private void pauseThreadA() {
        if (Thread.currentThread().getName().equals("A")) {
          paused.countDown();
          try {
            resume.await(10, java.util.concurrent.TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }

      @Override
      public List<Map<String, Object>> insert(List<? extends Map<String, Object>> newDocuments) {
        pauseThreadA();
        return super.insert(newDocuments);
      }

      @Override
      public List<Map<String, Object>> insertBuilt(
          java.util.function.Supplier<? extends List<? extends Map<String, Object>>> builder) {
        pauseThreadA();
        return super.insertBuilt(builder);
      }
    }

    @Test
    void eventsGetTheirCreatedAtInTheOrderTheyBecomeVisible(@TempDir Path directory) throws Exception {
      // Thread A is preempted on its way to the events lock while B stores an event of the same entity, and a
      // reduction then snapshots the entity at B's createdAt. Core later reduces from that snapshot with the events
      // created strictly after it, so A's event must not carry an older createdAt than B's.
      PausingDatastore store = new PausingDatastore(directory.resolve("events.json"));
      EventRegistry registry = new EventRegistry(store);
      DispatchJournal journal = new DispatchJournal(directory.resolve("dispatch_journal.json"));
      NonPersistedEventEnvelope eventA = Mocks.nonPersistedEvent();
      NonPersistedEventEnvelope eventB = eventA.toBuilder().value(map("from", "B")).build();
      try {
        java.util.concurrent.CompletableFuture<List<EventEnvelope>> storingA =
            new java.util.concurrent.CompletableFuture<>();
        Thread.ofVirtual().name("A").start(() -> {
          try {
            storingA.complete(EventsAdapter.storeEvents(mockUserApp, registry, journal, List.of(eventA), mockConfig));
          } catch (RuntimeException e) {
            storingA.completeExceptionally(e);
          }
        });
        assertThat(store.paused.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        Thread.sleep(5);
        EventEnvelope storedB =
            EventsAdapter.storeEvents(mockUserApp, registry, journal, List.of(eventB), mockConfig).get(0);
        String snapshotPoint = storedB.createdAt();
        store.resume.countDown();
        EventEnvelope storedA = storingA.get(10, java.util.concurrent.TimeUnit.SECONDS).get(0);

        assertThat(storedA.createdAt()).isGreaterThan(snapshotPoint);
        assertThat(EventsAdapter.readEntityEventsSince(registry, mockConfig, eventA.entityTypeName(),
            eventA.entityID(), snapshotPoint)).extracting(EventEnvelope::id).containsExactly(storedA.id());
      } finally {
        store.resume.countDown();
        store.close();
        journal.datastore().close();
      }
    }

    @Test
    void withTheJournalTheBatchIsStoredAtomically(@TempDir Path directory) throws IOException {
      // A crash, or a failed write, must never leave part of the events one command registered: the batch is one
      // insert. Here its second document is invalid for the store, so the insert fails before writing anything (the
      // TypeScript one-by-one loop, kept by the variant without journal, would have stored the first event).
      Path eventsFile = directory.resolve("events.json");
      EventRegistry registry = new EventRegistry(eventsFile);
      DispatchJournal journal = new DispatchJournal(directory.resolve("dispatch_journal.json"));
      NonPersistedEventEnvelope invalid = Mocks.nonPersistedEvent().toBuilder().value(map("$where", "x")).build();
      try {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EventsAdapter.storeEvents(mockUserApp, registry,
            journal, List.of(mockEventEnvelop, invalid), mockConfig))
            .hasMessageContaining("Field names cannot begin with the $ character");
        assertThat(registry.count()).isZero();
        assertThat(Files.exists(eventsFile) ? Files.readString(eventsFile) : "").isEmpty();
        assertThat(journal.size()).isZero();
        verify(mockUserApp, never()).boosterEventDispatcher(any());

        List<EventEnvelope> stored = EventsAdapter.storeEvents(mockUserApp, registry, journal,
            List.of(mockEventEnvelop, Mocks.nonPersistedEvent(), Mocks.nonPersistedEvent()), mockConfig);
        assertThat(registry.count()).isEqualTo(3);
        // one append: the batch header and the three events
        assertThat(Files.readAllLines(eventsFile)).hasSize(4).first().isEqualTo("{\"$$batch\":3}");
        assertThat(stored).extracting(EventEnvelope::id).doesNotContainNull().doesNotHaveDuplicates();
      } finally {
        registry.events().close();
        journal.datastore().close();
      }
    }

    @Test
    void aCrashThatCutsTheAppendOfABatchLeavesNoneOfItsEvents(@TempDir Path directory) throws IOException {
      // a power loss, or a short write killed halfway, can leave any prefix of the append on disk
      Path eventsFile = directory.resolve("events.json");
      EventRegistry registry = new EventRegistry(eventsFile);
      DispatchJournal journal = new DispatchJournal(directory.resolve("dispatch_journal.json"));
      try {
        EventsAdapter.storeEvents(mockUserApp, registry, journal, List.of(Mocks.nonPersistedEvent()), mockConfig);
        int before = Files.readAllBytes(eventsFile).length;
        EventsAdapter.storeEvents(mockUserApp, registry, journal,
            List.of(Mocks.nonPersistedEvent(), Mocks.nonPersistedEvent(), Mocks.nonPersistedEvent()), mockConfig);
        byte[] complete = Files.readAllBytes(eventsFile);
        for (int cut = before; cut < complete.length; cut += 7) {
          Path crashed = directory.resolve("crashed-" + cut + ".json");
          Files.write(crashed, java.util.Arrays.copyOf(complete, cut));
          EventRegistry restarted = new EventRegistry(crashed);
          try {
            assertThat(restarted.count()).as("cut at byte %d", cut).isIn(1, 4);
          } finally {
            restarted.events().close();
          }
        }
      } finally {
        registry.events().close();
        journal.datastore().close();
      }
    }
  }

  @Nested
  class StoreSnapshot {
    @Test
    void storesTheSnapshotWithItsLogicalAndPhysicalDates() {
      Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:28.050Z"), ZoneOffset.UTC));
      NonPersistedEntitySnapshotEnvelope snapshot = mockSnapshot.toNonPersisted();
      EntitySnapshotEnvelope stored = EventsAdapter.storeSnapshot(mockEventRegistry, snapshot, mockConfig);
      assertThat(stored.createdAt()).isEqualTo(snapshot.snapshottedEventCreatedAt());
      assertThat(stored.persistedAt()).isEqualTo("2026-11-10T09:15:28.050Z");
      verify(mockEventRegistry).store(raw(stored));
      verify(logger).debug("[Booster]|events-adapter#storeSnapshot: ", "Storing the following snapshot envelope:",
          snapshot);
      verify(logger).debug("[Booster]|events-adapter#storeSnapshot: ", "Snapshot stored");
    }
  }

  @Test
  void storeDispatchedEventIsAlwaysTrueWithoutJournal() {
    assertThat(EventsAdapter.storeDispatchedEvent()).isTrue();
  }
}
