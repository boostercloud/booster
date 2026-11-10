package com.boostercloud.framework.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventStoreEntryEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.InvalidEventError;
import com.boostercloud.framework.types.errors.InvalidReducerError;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.instrumentation.TraceInfo;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/services/event-store.test.ts}, plus the paths the TypeScript tests leave uncovered (spec 06 §5.9). */
class EventStoreTest extends BoosterTestSupport {

  static final AtomicInteger GET_ID_CALLS = new AtomicInteger();
  static final AtomicInteger GET_PREFIXED_ID_CALLS = new AtomicInteger();
  static final List<List<Object>> FAKE_REDUCER_CALLS = Collections.synchronizedList(new ArrayList<>());
  static final List<String> HOOK_CALLS = Collections.synchronizedList(new ArrayList<>());
  static final List<Object> HOOK_ARGUMENTS = Collections.synchronizedList(new ArrayList<>());
  static final List<Throwable> HOOK_RESULTS = Collections.synchronizedList(new ArrayList<>());

  public record AnEvent(UUID id, String entityId, int delta) {
    public UUID entityID() {
      return UUID.of(entityId);
    }
  }

  public record AnotherEvent(UUID id) {
    public UUID entityID() {
      return id;
    }

    public String getPrefixedId(String prefix) {
      GET_PREFIXED_ID_CALLS.incrementAndGet();
      return prefix + "-" + id;
    }
  }

  public record AnEntity(UUID id, int count) {
    public UUID getId() {
      GET_ID_CALLS.incrementAndGet();
      return id;
    }

    public static AnEntity reducerThatCallsEntityMethod(AnEvent event, AnEntity currentEntity) {
      if (currentEntity != null) {
        currentEntity.getId();
      }
      return new AnEntity(UUID.of(event.entityId()), event.delta());
    }

    public static AnEntity reducerThatCallsEventMethod(AnotherEvent event, AnEntity currentEntity) {
      event.getPrefixedId("prefix");
      return new AnEntity(UUID.of("1"), 1);
    }
  }

  /** Stands for the sinon fake reducer of the TypeScript tests. */
  public static final class FakeReducers {
    public static Object fakeReducer(Object event, Object currentEntity) {
      FAKE_REDUCER_CALLS.add(Arrays.asList(event, currentEntity));
      return Map.of("id", "42", "count", 1);
    }

    public static Object failingReducer(Object event, Object currentEntity) {
      throw new IllegalStateException("Error on reducer");
    }
  }

  /** A global error handler whose hooks return the queued results (or the error when nothing is queued). */
  public static final class RecordingErrorHandler {
    public static Throwable onEventError(Throwable error, EventEnvelope eventEnvelope) {
      HOOK_CALLS.add("onEventError");
      HOOK_ARGUMENTS.add(eventEnvelope);
      return next(error);
    }

    public static Throwable onReducerError(Throwable error, EventEnvelope eventEnvelope, ReducerMetadata reducerMetadata,
        Object eventInstance, Object snapshotInstance) {
      HOOK_CALLS.add("onReducerError");
      HOOK_ARGUMENTS.add(Arrays.asList(error, eventEnvelope, reducerMetadata, eventInstance, snapshotInstance));
      return next(error);
    }

    public static Throwable onError(Throwable error) {
      return error;
    }

    private static Throwable next(Throwable error) {
      if (HOOK_RESULTS.isEmpty()) {
        return error;
      }
      Throwable result = HOOK_RESULTS.removeFirst();
      return result == SAME ? error : result;
    }
  }

  /** Queued in {@link #HOOK_RESULTS}: return the received error. */
  static final Throwable SAME = new Throwable("same");

  public record AnEventV1(UUID id, String entity, int delta) {}

  public static final class AnEventMigration {
    public AnEvent toV2(AnEventV1 old) {
      return new AnEvent(old.id(), old.entity(), old.delta() * 10);
    }
  }

  public record AnEntityV1(UUID id, int total) {}

  public static final class AnEntityMigration {
    public AnEntity toV2(AnEntityV1 old) {
      return new AnEntity(old.id(), old.total());
    }
  }

  static final String IMPORTANT_DATE_TIME_STAMP = "2019-12-23T06:30:00.000Z";
  static final String ORIGIN_OF_TIME = "1970-01-01T00:00:00.000Z";

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private Logger logger;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    GET_ID_CALLS.set(0);
    GET_PREFIXED_ID_CALLS.set(0);
    FAKE_REDUCER_CALLS.clear();
    HOOK_CALLS.clear();
    HOOK_ARGUMENTS.clear();
    HOOK_RESULTS.clear();
    config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    config.entities.put("AnEntity", new EntityMetadata(AnEntity.class, BoosterAuthorizer.authorizeRoles(List.of())));
    config.reducers.put("AnEvent", new ReducerMetadata(AnEntity.class, "reducerThatCallsEntityMethod"));
    config.reducers.put("AnotherEvent", new ReducerMetadata(AnEntity.class, "reducerThatCallsEventMethod"));
    config.events.put("AnEvent", new EventMetadata(AnEvent.class));
    config.events.put("AnotherEvent", new EventMetadata(AnotherEvent.class));
    logger = mock(Logger.class);
    config.logger = logger;
    config.eventStoreRetry.initialDelay = 1;
    config.eventStoreRetry.maxDelay = 2;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------------------------

  static Map<String, Object> someEvent() {
    return rawMap("id", "1", "entityId", "42", "delta", 1);
  }

  static Map<String, Object> otherEvent() {
    return rawMap("entityId", "42", "delta", 2);
  }

  static Map<String, Object> someEntity() {
    return rawMap("id", "42", "count", 0);
  }

  static Map<String, Object> rawMap(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  static EventEnvelope eventEnvelopeFor(Object event, String typeName, String createdAt) {
    return EventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of("42"))
        .entityTypeName("AnEntity")
        .value(event)
        .requestID(UUID.of("whatever"))
        .typeName(typeName)
        .createdAt(createdAt)
        .build();
  }

  static EventEnvelope eventEnvelopeFor(Object event, String typeName) {
    return eventEnvelopeFor(event, typeName, "2026-11-10T10:00:00.000Z");
  }

  /** A "BEM" (entity migrated) event as the TypeScript tests build it: a domain event named {@code bemEvent}. */
  static EventEnvelope bemEventFor(String newId, int delta) {
    return eventEnvelopeFor(rawMap("entityId", 42, "delta", delta, "superKind", "booster", "newEntity", rawMap("id", newId)),
        "bemEvent");
  }

  static NonPersistedEntitySnapshotEnvelope snapshotEnvelopeFor(Map<String, Object> entity) {
    return NonPersistedEntitySnapshotEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of(String.valueOf(entity.get("id"))))
        .entityTypeName("AnEntity")
        .value(entity)
        .requestID(UUID.of("whatever"))
        .typeName("AnEntity")
        .snapshottedEventCreatedAt(IMPORTANT_DATE_TIME_STAMP)
        .build();
  }

  static NonPersistedEntitySnapshotEnvelope snapshotFor(String id, int count) {
    return snapshotEnvelopeFor(rawMap("id", id, "count", count));
  }

  /** What a provider returns: the snapshot with its persistence fields. */
  static EntitySnapshotEnvelope persisted(NonPersistedEntitySnapshotEnvelope snapshot) {
    return snapshot.persisted(IMPORTANT_DATE_TIME_STAMP);
  }

  /** Stubs {@code entityReducer} with the given results (a {@link Throwable} is thrown) and records its arguments. */
  static List<List<Object>> stubEntityReducer(EventStore eventStore, List<?> results) {
    List<List<Object>> calls = Collections.synchronizedList(new ArrayList<>());
    doAnswer(invocation -> {
      calls.add(Arrays.asList(invocation.getArgument(0), invocation.getArgument(1)));
      Object result = results.get(calls.size() - 1);
      if (result instanceof Throwable error) {
        throw error;
      }
      return result;
    }).when(eventStore).entityReducer(any(), any());
    return calls;
  }

  private EventStore storeSpy() {
    return spy(new EventStore(config));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Public methods
  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class FetchEntitySnapshot {

    @Test
    void reducesThroughTheInstanceEntityReducer() {
      // TypeScript: "properly binds `this` to the entityReducer"
      EventStore eventStore = storeSpy();
      doReturn(null).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(List.of(eventEnvelopeFor(someEvent(), "AnEvent"))).when(eventStore).loadEventStreamSince(any(), any(), any());
      List<List<Object>> calls = stubEntityReducer(eventStore, Collections.singletonList(null));

      assertThat(eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"))).isEmpty();
      assertThat(calls).hasSize(1);
    }

    @Test
    void returnsTheSnapshotWhenThereAreNoPendingEvents() {
      EventStore eventStore = storeSpy();
      EntitySnapshotEnvelope someSnapshotEnvelope = persisted(snapshotEnvelopeFor(someEntity()));
      doReturn(someSnapshotEnvelope).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(List.of()).when(eventStore).loadEventStreamSince(any(), any(), any());

      Optional<EntitySnapshotEnvelope> entity = eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadLatestSnapshot("AnEntity", UUID.of("42"));
      verify(eventStore, times(1)).loadEventStreamSince("AnEntity", UUID.of("42"), IMPORTANT_DATE_TIME_STAMP);
      verify(eventStore, never()).entityReducer(any(), any());
      verify(eventStore, never()).storeSnapshot(any());
      assertThat(entity).containsSame(someSnapshotEnvelope);
    }

    @Test
    void reducesAShortListOfPendingEventsAndReturnsWhatStoreSnapshotReturns() {
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope someSnapshotEnvelope = snapshotEnvelopeFor(someEntity());
      EventEnvelope someEventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent", "2026-11-10T10:00:00.000Z");
      EventEnvelope otherEventEnvelope = eventEnvelopeFor(otherEvent(), "AnEvent", "2026-11-10T10:00:00.100Z");
      doReturn(persisted(someSnapshotEnvelope)).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(List.of(someEventEnvelope, otherEventEnvelope)).when(eventStore).loadEventStreamSince(any(), any(), any());
      List<List<Object>> calls = stubEntityReducer(eventStore, List.of(snapshotFor("42", 1), snapshotFor("42", 3)));
      EntitySnapshotEnvelope expectedResult = persisted(snapshotFor("42", 3));
      doReturn(expectedResult).when(eventStore).storeSnapshot(any());

      Optional<EntitySnapshotEnvelope> entity = eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadLatestSnapshot("AnEntity", UUID.of("42"));
      verify(eventStore, times(1)).loadEventStreamSince("AnEntity", UUID.of("42"), IMPORTANT_DATE_TIME_STAMP);
      assertThat(calls).containsExactly(
          List.of(someEventEnvelope, someSnapshotEnvelope),
          List.of(otherEventEnvelope, snapshotFor("42", 1)));
      verify(eventStore).storeSnapshot(snapshotFor("42", 3));
      // Directly returns the value returned by storeSnapshot
      assertThat(entity).containsSame(expectedResult);
    }

    @Test
    void reducesALongListOfPendingEventsStartingFromTheLoadedSnapshot() {
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope someSnapshotEnvelope = snapshotEnvelopeFor(someEntity());
      EventEnvelope someEventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent");
      EventEnvelope otherEventEnvelope = eventEnvelopeFor(otherEvent(), "AnEvent");
      List<EventEnvelope> pendingEvents = List.of(someEventEnvelope, otherEventEnvelope, someEventEnvelope,
          otherEventEnvelope, someEventEnvelope, otherEventEnvelope);
      List<Integer> results = List.of(1, 3, 4, 6, 7, 9);
      List<NonPersistedEntitySnapshotEnvelope> outputs = results.stream().map(count -> snapshotFor("42", count)).toList();
      doReturn(persisted(someSnapshotEnvelope)).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(pendingEvents).when(eventStore).loadEventStreamSince(any(), any(), any());
      List<List<Object>> calls = stubEntityReducer(eventStore, outputs);
      EntitySnapshotEnvelope expectedResult = persisted(snapshotFor("42", 9));
      doReturn(expectedResult).when(eventStore).storeSnapshot(any());

      Optional<EntitySnapshotEnvelope> entity = eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadEventStreamSince("AnEntity", UUID.of("42"), IMPORTANT_DATE_TIME_STAMP);
      assertThat(calls).hasSize(6);
      for (int index = 0; index < results.size(); index++) {
        assertThat(calls.get(index).get(0)).isEqualTo(pendingEvents.get(index));
        assertThat(calls.get(index).get(1)).isEqualTo(index == 0 ? someSnapshotEnvelope : outputs.get(index - 1));
      }
      verify(eventStore).storeSnapshot(outputs.get(5));
      assertThat(entity).containsSame(expectedResult);
    }

    @Test
    void reducesFromTheOriginOfTimeWhenThereIsNoSnapshot() {
      EventStore eventStore = storeSpy();
      EventEnvelope someEventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent");
      EventEnvelope otherEventEnvelope = eventEnvelopeFor(otherEvent(), "AnEvent");
      List<EventEnvelope> pendingEvents = List.of(someEventEnvelope, otherEventEnvelope, someEventEnvelope,
          otherEventEnvelope, someEventEnvelope, otherEventEnvelope);
      List<NonPersistedEntitySnapshotEnvelope> outputs =
          List.of(1, 3, 4, 6, 7, 9).stream().map(count -> snapshotFor("42", count)).toList();
      doReturn(null).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(pendingEvents).when(eventStore).loadEventStreamSince(any(), any(), any());
      List<List<Object>> calls = stubEntityReducer(eventStore, outputs);
      EntitySnapshotEnvelope expectedResult = persisted(snapshotFor("42", 9));
      doReturn(expectedResult).when(eventStore).storeSnapshot(any());

      Optional<EntitySnapshotEnvelope> entity = eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadLatestSnapshot("AnEntity", UUID.of("42"));
      verify(eventStore, times(1)).loadEventStreamSince("AnEntity", UUID.of("42"), ORIGIN_OF_TIME);
      assertThat(calls.get(0)).isEqualTo(Arrays.asList(pendingEvents.get(0), null));
      for (int index = 1; index < outputs.size(); index++) {
        assertThat(calls.get(index)).isEqualTo(List.of(pendingEvents.get(index), outputs.get(index - 1)));
      }
      verify(eventStore).storeSnapshot(outputs.get(5));
      assertThat(entity).containsSame(expectedResult);
    }

    @Test
    void returnsNothingWithoutSnapshotAndEvents() {
      EventStore eventStore = storeSpy();
      doReturn(null).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(List.of()).when(eventStore).loadEventStreamSince(any(), any(), any());

      Optional<EntitySnapshotEnvelope> entity = eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadEventStreamSince("AnEntity", UUID.of("42"), ORIGIN_OF_TIME);
      verify(eventStore, never()).entityReducer(any(), any());
      verify(eventStore, never()).storeSnapshot(any());
      assertThat(entity).isEmpty();
    }

    @Test
    void chainsTheReductionsThroughEntityMigratedEventsAndStoresOnlyTheFinalSnapshot() {
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope someSnapshotEnvelope = snapshotEnvelopeFor(someEntity());
      doReturn(persisted(someSnapshotEnvelope)).when(eventStore).loadLatestSnapshot(any(), any());
      List<String> reducersIds = List.of("42", "90", "42", "91", "42", "92"); // BEM events could return a different ID
      List<Integer> reducersCount = List.of(0, 1, 3, 4, 6, 7);
      List<NonPersistedEntitySnapshotEnvelope> outputs = new ArrayList<>();
      for (int index = 0; index < reducersIds.size(); index++) {
        outputs.add(snapshotFor(reducersIds.get(index), reducersCount.get(index)));
      }
      List<List<Object>> calls = stubEntityReducer(eventStore, outputs);
      doReturn(null).when(eventStore).storeSnapshot(any());
      EventEnvelope someEventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent");
      List<EventEnvelope> pendingEvents = List.of(someEventEnvelope, bemEventFor("90", 2), someEventEnvelope,
          bemEventFor("91", 2), someEventEnvelope, bemEventFor("92", 2));
      doReturn(pendingEvents).when(eventStore).loadEventStreamSince(any(), any(), any());

      eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadLatestSnapshot("AnEntity", UUID.of("42"));
      List<Integer> expectedCounts = List.of(0, 0, 1, 3, 4, 6);
      List<String> expectedIds = List.of("42", "42", "90", "42", "91", "42");
      for (int index = 0; index < pendingEvents.size(); index++) {
        assertThat(calls.get(index).get(0)).isEqualTo(pendingEvents.get(index));
        assertThat(calls.get(index).get(1)).isEqualTo(snapshotFor(expectedIds.get(index), expectedCounts.get(index)));
      }
      verify(eventStore, times(1)).storeSnapshot(any());
      verify(eventStore).storeSnapshot(snapshotFor("92", 7));
      verify(logger).debug(eq("[Booster]|EventStore#fetchEntitySnapshot: "),
          eq("Migrated entity AnEntity with previous ID 42 to AnEntity with the new ID 92"), eq(snapshotFor("92", 7)));
    }

    @Test
    void haltsTheReductionWhenAReducerFailsAndStoresNothing() {
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope someSnapshotEnvelope = snapshotEnvelopeFor(someEntity());
      doReturn(persisted(someSnapshotEnvelope)).when(eventStore).loadLatestSnapshot(any(), any());
      List<String> reducersIds = List.of("42", "90", "42", "91", "42", "92");
      List<Object> outputs = new ArrayList<>();
      for (int index = 0; index < 6; index++) {
        outputs.add(index == 2 ? new IllegalStateException("Error on reducer") : snapshotFor(reducersIds.get(index), index + 1));
      }
      List<List<Object>> calls = stubEntityReducer(eventStore, outputs);
      List<EventEnvelope> someEventEnvelopes = List.of(1, 3, 5).stream()
          .map(delta -> eventEnvelopeFor(rawMap("id", "1", "entityId", "42", "delta", delta), "AnEvent")).toList();
      List<EventEnvelope> pendingEvents = List.of(someEventEnvelopes.get(0), bemEventFor("90", 2), someEventEnvelopes.get(1),
          bemEventFor("91", 4), someEventEnvelopes.get(2), bemEventFor("92", 6));
      doReturn(pendingEvents).when(eventStore).loadEventStreamSince(any(), any(), any());

      assertThatThrownBy(() -> eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Error on reducer");

      assertThat(calls).hasSize(3);
      assertThat(calls.get(0)).isEqualTo(List.of(pendingEvents.get(0), someSnapshotEnvelope));
      assertThat(calls).extracting(call -> call.get(0)).containsExactly(pendingEvents.get(0), pendingEvents.get(1),
          pendingEvents.get(2));
      verify(eventStore, never()).storeSnapshot(any());
    }

    @Test
    void returnsNothingWithoutThrowingWhenStoringTheSnapshotFailsAfterEveryRetry() {
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope someSnapshotEnvelope = snapshotEnvelopeFor(someEntity());
      doReturn(persisted(someSnapshotEnvelope)).when(eventStore).loadLatestSnapshot(any(), any());
      List<String> reducersReturnIds = List.of("42", "90", "42", "91", "42", "92");
      List<NonPersistedEntitySnapshotEnvelope> outputs = new ArrayList<>();
      for (int index = 0; index < 6; index++) {
        outputs.add(snapshotFor(reducersReturnIds.get(index), index + 1));
      }
      List<List<Object>> calls = stubEntityReducer(eventStore, outputs);
      when(events.storeSnapshot(any(), any())).thenThrow(new IllegalStateException("Error on persist"));
      EventEnvelope someEventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent");
      List<EventEnvelope> pendingEvents = List.of(someEventEnvelope, bemEventFor("90", 2), someEventEnvelope,
          bemEventFor("91", 2), someEventEnvelope, bemEventFor("92", 2));
      doReturn(pendingEvents).when(eventStore).loadEventStreamSince(any(), any(), any());

      Optional<EntitySnapshotEnvelope> entity = eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      for (int index = 0; index < pendingEvents.size(); index++) {
        assertThat(calls.get(index).get(0)).isEqualTo(pendingEvents.get(index));
        assertThat(calls.get(index).get(1)).isEqualTo(index == 0 ? someSnapshotEnvelope : outputs.get(index - 1));
      }
      verify(eventStore, times(1)).storeSnapshot(any());
      verify(events, times(3)).storeSnapshot(outputs.get(5), config); // default number of retries is 3
      assertThat(entity).isEmpty();
    }

    // This could potentially happen if two or more processes are reading the same entity stream at the same time
    @Test
    void ignoresASnapshotInTheMiddleOfTheStream() {
      EventStore eventStore = storeSpy();
      doReturn(null).when(eventStore).loadLatestSnapshot(any(), any());
      EventEnvelope someEventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent");
      EventEnvelope otherEventEnvelope = eventEnvelopeFor(otherEvent(), "AnEvent");
      List<EventStoreEntryEnvelope> pendingEvents = List.of(someEventEnvelope, otherEventEnvelope, someEventEnvelope,
          snapshotEnvelopeFor(someEntity()), otherEventEnvelope, someEventEnvelope, otherEventEnvelope);
      doReturn(pendingEvents).when(eventStore).loadEventStreamSince(any(), any(), any());
      List<NonPersistedEntitySnapshotEnvelope> outputs =
          List.of(1, 3, 4, 6, 7, 9).stream().map(count -> snapshotFor("42", count)).toList();
      List<List<Object>> calls = stubEntityReducer(eventStore, outputs);
      doReturn(null).when(eventStore).storeSnapshot(any());

      eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, times(1)).loadEventStreamSince("AnEntity", UUID.of("42"), ORIGIN_OF_TIME);
      assertThat(calls).hasSize(6);
      assertThat(calls.get(0)).isEqualTo(Arrays.asList(pendingEvents.get(0), null));
      for (int index = 1; index < outputs.size(); index++) {
        int eventIndex = index > 2 ? index + 1 : index; // skip the snapshot
        assertThat(calls.get(index)).isEqualTo(List.of(pendingEvents.get(eventIndex), outputs.get(index - 1)));
      }
      verify(eventStore, times(1)).storeSnapshot(any());
    }

    @Test
    void storesThePreviousSnapshotAgainWhenEveryPendingEntryWasSkipped() {
      // TypeScript quirk kept: only "no snapshot at all" skips storing
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope someSnapshotEnvelope = snapshotEnvelopeFor(someEntity());
      doReturn(persisted(someSnapshotEnvelope)).when(eventStore).loadLatestSnapshot(any(), any());
      doReturn(List.of(snapshotFor("42", 5))).when(eventStore).loadEventStreamSince(any(), any(), any());
      doReturn(null).when(eventStore).storeSnapshot(any());

      eventStore.fetchEntitySnapshot("AnEntity", UUID.of("42"));

      verify(eventStore, never()).entityReducer(any(), any());
      verify(eventStore).storeSnapshot(someSnapshotEnvelope);
    }

    @Test
    void reducesStoresAndReturnsWithTheRegisteredReducersAndSchemaMigrations() {
      config.schemaMigrations.put("AnEvent",
          Map.of(2, new SchemaMigrationMetadata(AnEventMigration.class, "toV2", 2, AnEventV1.class, AnEvent.class)));
      config.schemaMigrations.put("AnEntity",
          Map.of(2, new SchemaMigrationMetadata(AnEntityMigration.class, "toV2", 2, AnEntityV1.class, AnEntity.class)));
      EntitySnapshotEnvelope storedV1Snapshot = NonPersistedEntitySnapshotEnvelope.builder().version(1)
          .superKind(SuperKindType.DOMAIN).requestID(UUID.of("old")).entityID(UUID.of("42")).entityTypeName("AnEntity")
          .typeName("AnEntity").value(rawMap("id", "42", "total", 5)).snapshottedEventCreatedAt(IMPORTANT_DATE_TIME_STAMP)
          .build().persisted("2019-12-23T06:30:00.500Z");
      when(events.latestEntitySnapshot(config, "AnEntity", UUID.of("42"))).thenReturn(Optional.of(storedV1Snapshot));
      EventEnvelope v1Event = eventEnvelopeFor(rawMap("id", "1", "entity", "42", "delta", 3), "AnEvent",
          "2026-11-10T10:00:00.000Z");
      EventEnvelope v2Event = eventEnvelopeFor(someEvent(), "AnEvent", "2026-11-10T10:00:01.000Z").toBuilder()
          .version(2).requestID(UUID.of("last")).build();
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), IMPORTANT_DATE_TIME_STAMP))
          .thenReturn(List.of(v1Event, v2Event));
      when(events.storeSnapshot(any(), any())).thenAnswer(invocation ->
          invocation.<NonPersistedEntitySnapshotEnvelope>getArgument(0).persisted("2026-11-10T10:00:02.000Z"));

      Optional<EntitySnapshotEnvelope> result = new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"));

      NonPersistedEntitySnapshotEnvelope expected = NonPersistedEntitySnapshotEnvelope.builder()
          .version(2)
          .superKind(SuperKindType.DOMAIN)
          .requestID(UUID.of("last"))
          .entityID(UUID.of("42"))
          .entityTypeName("AnEntity")
          .typeName("AnEntity")
          .value(new AnEntity(UUID.of("42"), 1))
          .snapshottedEventCreatedAt("2026-11-10T10:00:01.000Z")
          .build();
      verify(events).storeSnapshot(expected, config);
      assertThat(result).contains(expected.persisted("2026-11-10T10:00:02.000Z"));
      // the stored v1 snapshot was migrated before the first reduction (the reducer called its instance method)
      assertThat(GET_ID_CALLS).hasValue(2);
    }

    // data-migrations.md: schema migrations are lazy and nothing is written back until a new snapshot is reduced
    @Test
    void returnsTheMigratedSnapshotWithoutStoringItBackWhenThereAreNoPendingEvents() {
      config.schemaMigrations.put("AnEntity",
          Map.of(2, new SchemaMigrationMetadata(AnEntityMigration.class, "toV2", 2, AnEntityV1.class, AnEntity.class)));
      EntitySnapshotEnvelope storedV1Snapshot = NonPersistedEntitySnapshotEnvelope.builder().version(1)
          .superKind(SuperKindType.DOMAIN).requestID(UUID.of("old")).entityID(UUID.of("42")).entityTypeName("AnEntity")
          .typeName("AnEntity").value(rawMap("id", "42", "total", 5)).snapshottedEventCreatedAt(IMPORTANT_DATE_TIME_STAMP)
          .build().persisted("2019-12-23T06:30:00.500Z");
      when(events.latestEntitySnapshot(config, "AnEntity", UUID.of("42"))).thenReturn(Optional.of(storedV1Snapshot));
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), IMPORTANT_DATE_TIME_STAMP)).thenReturn(List.of());

      Optional<EntitySnapshotEnvelope> result = new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"));

      assertThat(result).get().satisfies(snapshot -> {
        assertThat(snapshot.version()).isEqualTo(2);
        assertThat(snapshot.value()).isEqualTo(new AnEntity(UUID.of("42"), 5));
        // every other field of the stored envelope is kept ({ ...envelope, value, version })
        assertThat(snapshot).isEqualTo(storedV1Snapshot.toBuilder().version(2).value(new AnEntity(UUID.of("42"), 5)).build());
      });
      verify(events, never()).storeSnapshot(any(), any());
    }

    @Test
    void failsWhenTheLatestSnapshotHasAnUnknownSchemaVersion() {
      EntitySnapshotEnvelope futureSnapshot = NonPersistedEntitySnapshotEnvelope.builder().version(4)
          .superKind(SuperKindType.DOMAIN).requestID(UUID.of("old")).entityID(UUID.of("42")).entityTypeName("AnEntity")
          .typeName("AnEntity").value(rawMap("id", "42", "count", 5)).snapshottedEventCreatedAt(IMPORTANT_DATE_TIME_STAMP)
          .build().persisted("2019-12-23T06:30:00.500Z");
      when(events.latestEntitySnapshot(config, "AnEntity", UUID.of("42"))).thenReturn(Optional.of(futureSnapshot));

      assertThatThrownBy(() -> new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42")))
          .isInstanceOf(InvalidVersionError.class)
          .hasMessage("Can not migrate schema an unknown version: The current schema version of AnEntity is 1, which is"
              + " lower than the received version 4");
      verify(events, never()).forEntitySince(any(), any(), any(), any());
      verify(events, never()).storeSnapshot(any(), any());
    }

    @Test
    void failsWhenAnEventHasAnUnknownSchemaVersion() {
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(eventEnvelopeFor(someEvent(), "AnEvent").toBuilder().version(3).build()));

      assertThatThrownBy(() -> new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42")))
          .isInstanceOf(InvalidVersionError.class)
          .hasMessage("Can not migrate schema an unknown version: The current schema version of AnEvent is 1, which is"
              + " lower than the received version 3");
      verify(events, never()).storeSnapshot(any(), any());
    }

    @Test
    void producesTheSnapshotOfTheNewEntityForAnEntityMigratedEvent() {
      EventEnvelope migrated = EventEnvelope.builder()
          .version(1).superKind(SuperKindType.BOOSTER).entityID(UUID.of("old-1")).entityTypeName("OldEntity")
          .typeName("BoosterEntityMigrated").requestID(UUID.of("r1")).createdAt("2026-11-10T10:00:00.000Z")
          .value(rawMap("oldEntityName", "OldEntity", "oldEntityId", "old-1", "newEntityName", "AnEntity",
              "newEntity", rawMap("id", "new-1", "count", 9)))
          .build();
      when(events.forEntitySince(config, "OldEntity", UUID.of("old-1"), ORIGIN_OF_TIME)).thenReturn(List.of(migrated));
      when(events.storeSnapshot(any(), any())).thenAnswer(invocation ->
          invocation.<NonPersistedEntitySnapshotEnvelope>getArgument(0).persisted("2026-11-10T10:00:02.000Z"));

      Optional<EntitySnapshotEnvelope> result = new EventStore(config).fetchEntitySnapshot("OldEntity", UUID.of("old-1"));

      assertThat(result).get().satisfies(snapshot -> {
        assertThat(snapshot.entityTypeName()).isEqualTo("AnEntity");
        assertThat(snapshot.typeName()).isEqualTo("AnEntity");
        assertThat(snapshot.entityID()).isEqualTo(UUID.of("new-1"));
        assertThat(snapshot.superKind()).isEqualTo(SuperKindType.BOOSTER);
        assertThat(snapshot.value()).isEqualTo(rawMap("id", "new-1", "count", 9));
      });
      verify(logger).debug(eq("[Booster]|EventStore#fetchEntitySnapshot: "),
          eq("Migrated entity OldEntity with previous ID old-1 to AnEntity with the new ID new-1"), any());
    }

    @Test
    void reducesAStoredEntityMigratedRecordValue() {
      // The value of a stored event can also be the event instance (local providers in the same process)
      EventEnvelope migrated = EventEnvelope.builder()
          .version(1).superKind(SuperKindType.BOOSTER).entityID(UUID.of("old-1")).entityTypeName("OldEntity")
          .typeName("BoosterEntityMigrated").requestID(UUID.of("r1")).createdAt("2026-11-10T10:00:00.000Z")
          .value(new BoosterEntityMigrated("OldEntity", UUID.of("old-1"), "AnEntity", new AnEntity(UUID.of("new-1"), 9)))
          .build();

      NonPersistedEntitySnapshotEnvelope snapshot = new EventStore(config).entityReducer(migrated, null);

      assertThat(snapshot.entityID()).isEqualTo(UUID.of("new-1"));
      assertThat(snapshot.value()).isEqualTo(new AnEntity(UUID.of("new-1"), 9));
    }
  }

  @Nested
  class BuiltInTouchedEvents {

    private EventEnvelope touch(String createdAt) {
      return EventEnvelope.builder()
          .version(1).superKind(SuperKindType.BOOSTER).entityID(UUID.of("42")).entityTypeName("AnEntity")
          .typeName("BoosterEntityTouched").requestID(UUID.of("touch-request")).createdAt(createdAt)
          .value(rawMap("entityName", "AnEntity", "entityId", "42"))
          .build();
    }

    @Test
    void reducesTheSameStateWithTheTimestampOfTheTouch() {
      NonPersistedEntitySnapshotEnvelope latest = snapshotEnvelopeFor(rawMap("id", "42", "count", 3));

      NonPersistedEntitySnapshotEnvelope snapshot =
          new EventStore(config).entityReducer(touch("2026-11-10T10:00:00.000Z"), latest);

      assertThat(snapshot).isEqualTo(NonPersistedEntitySnapshotEnvelope.builder()
          .version(1)
          .superKind(SuperKindType.BOOSTER)
          .requestID(UUID.of("touch-request"))
          .entityID(UUID.of("42"))
          .entityTypeName("AnEntity")
          .typeName("AnEntity")
          .value(new AnEntity(UUID.of("42"), 3))
          .snapshottedEventCreatedAt("2026-11-10T10:00:00.000Z")
          .build());
    }

    @Test
    void reducesATouchRecordValue() {
      EventEnvelope touch = touch("2026-11-10T10:00:00.000Z").toBuilder()
          .value(new BoosterEntityTouched("AnEntity", UUID.of("42"))).build();

      NonPersistedEntitySnapshotEnvelope snapshot =
          new EventStore(config).entityReducer(touch, snapshotEnvelopeFor(rawMap("id", "42", "count", 3)));

      assertThat(snapshot.value()).isEqualTo(new AnEntity(UUID.of("42"), 3));
    }

    @Test
    void reducesToNothingWithoutState() {
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(touch("2026-11-10T10:00:00.000Z")));

      assertThat(new EventStore(config).entityReducer(touch("2026-11-10T10:00:00.000Z"), null)).isNull();
      assertThat(new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"))).isEmpty();
      verify(events, never()).storeSnapshot(any(), any());
      verify(logger).debug("[Booster]|EventStore#fetchEntitySnapshot: ", "No snapshot was found or reduced, returning");
    }

    @Test
    void doesNotNeedAReducerOrAnEventRegistration() {
      config.events.clear();
      config.reducers.clear();

      assertThat(new EventStore(config).entityReducer(touch("2026-11-10T10:00:00.000Z"), snapshotFor("42", 1)))
          .isNotNull();
    }
  }

  @Nested
  class ReductionErrors {

    @BeforeEach
    void useTheRecordingErrorHandler() {
      config.globalErrorsHandler = new GlobalErrorHandlerMetadata(RecordingErrorHandler.class);
    }

    @Test
    void anUnregisteredEventFailsTheFetchWithoutGlobalHandler() {
      config.globalErrorsHandler = null;
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(eventEnvelopeFor(someEvent(), "UnknownEvent")));

      assertThatThrownBy(() -> new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42")))
          .isInstanceOf(InvalidEventError.class)
          .hasMessage("No event registered for event UnknownEvent");
      verify(logger).error("[Booster]|eventMetadataFor: ", "No event registered for event UnknownEvent");
      verify(events, never()).storeSnapshot(any(), any());
    }

    @Test
    void anUnregisteredEventIsSkippedWhenTheGlobalHandlerSwallowsTheError() {
      HOOK_RESULTS.add(null);
      EventEnvelope unknown = eventEnvelopeFor(someEvent(), "UnknownEvent", "2026-11-10T10:00:00.000Z");
      EventEnvelope known = eventEnvelopeFor(someEvent(), "AnEvent", "2026-11-10T10:00:01.000Z");
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME)).thenReturn(List.of(unknown, known));

      new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"));

      assertThat(HOOK_CALLS).containsExactly("onEventError");
      assertThat(HOOK_ARGUMENTS).containsExactly(unknown);
      verify(events).storeSnapshot(NonPersistedEntitySnapshotEnvelope.builder().version(1).superKind(SuperKindType.DOMAIN)
          .requestID(UUID.of("whatever")).entityID(UUID.of("42")).entityTypeName("AnEntity").typeName("AnEntity")
          .value(new AnEntity(UUID.of("42"), 1)).snapshottedEventCreatedAt("2026-11-10T10:00:01.000Z").build(), config);
    }

    @Test
    void anUnregisteredEventFailsTheFetchWithTheErrorOfTheGlobalHandler() {
      IllegalStateException handled = new IllegalStateException("handled");
      HOOK_RESULTS.add(handled);
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(eventEnvelopeFor(someEvent(), "UnknownEvent")));

      assertThatThrownBy(() -> new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"))).isSameAs(handled);
    }

    @Test
    void aMissingReducerFailsTheFetchWithoutGlobalHandler() {
      config.globalErrorsHandler = null;
      config.reducers.remove("AnEvent");
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(eventEnvelopeFor(someEvent(), "AnEvent")));

      assertThatThrownBy(() -> new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42")))
          .isInstanceOf(InvalidReducerError.class)
          .hasMessage("No reducer registered for event AnEvent");
    }

    @Test
    void aMissingReducerIsDispatchedTwiceAndSkippedWhenTheSecondDispatchSwallowsIt() {
      // TypeScript quirk kept: createNewSnapshot dispatches first, then the reduction loop dispatches again
      config.reducers.remove("AnEvent");
      HOOK_RESULTS.add(SAME);
      HOOK_RESULTS.add(null);
      EventEnvelope event = eventEnvelopeFor(someEvent(), "AnEvent");
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME)).thenReturn(List.of(event));

      assertThat(new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"))).isEmpty();

      assertThat(HOOK_CALLS).containsExactly("onReducerError", "onReducerError");
      @SuppressWarnings("unchecked")
      List<Object> first = (List<Object>) HOOK_ARGUMENTS.getFirst();
      assertThat(first.get(0)).isInstanceOf(InvalidReducerError.class);
      assertThat(first.get(1)).isEqualTo(event);
      assertThat(first.get(2)).isNull(); // there is no reducer metadata
      assertThat(first.get(3)).isEqualTo(new AnEvent(UUID.of("1"), "42", 1));
      assertThat(first.get(4)).isNull();
      assertThat(HOOK_ARGUMENTS.get(1)).isEqualTo(first);
      verify(events, never()).storeSnapshot(any(), any());
    }

    @Test
    void aFailingReducerGoesToTheGlobalHandlerAndItsErrorIsThrown() {
      config.reducers.put("AnEvent", new ReducerMetadata(FakeReducers.class, "failingReducer"));
      IllegalArgumentException handled = new IllegalArgumentException("handled reducer error");
      HOOK_RESULTS.add(handled);
      NonPersistedEntitySnapshotEnvelope latest = snapshotFor("42", 4);
      EventEnvelope event = eventEnvelopeFor(someEvent(), "AnEvent");

      assertThatThrownBy(() -> new EventStore(config).entityReducer(event, latest)).isSameAs(handled);

      @SuppressWarnings("unchecked")
      List<Object> arguments = (List<Object>) HOOK_ARGUMENTS.getFirst();
      assertThat((Throwable) arguments.get(0)).isInstanceOf(IllegalStateException.class).hasMessage("Error on reducer");
      assertThat(arguments.get(1)).isEqualTo(event);
      assertThat(arguments.get(2)).isEqualTo(new ReducerMetadata(FakeReducers.class, "failingReducer"));
      assertThat(arguments.get(3)).isEqualTo(new AnEvent(UUID.of("1"), "42", 1));
      assertThat(arguments.get(4)).isEqualTo(new AnEntity(UUID.of("42"), 4));
    }

    @Test
    void aReducerErrorSwallowedByTheGlobalHandlerStillStopsTheReduction() {
      config.reducers.put("AnEvent", new ReducerMetadata(FakeReducers.class, "failingReducer"));
      HOOK_RESULTS.add(null);
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(eventEnvelopeFor(someEvent(), "AnEvent"), eventEnvelopeFor(otherEvent(), "AnEvent")));

      assertThatThrownBy(() -> new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42")))
          .isInstanceOf(IgnoredReducerError.class)
          .hasMessage("The global error handler ignored the error of the reducer for event AnEvent of entity AnEntity"
              + " with ID 42, but reducer errors cannot be ignored: the entity snapshot was not updated")
          .cause().hasMessage("Error on reducer");
      assertThat(HOOK_CALLS).containsExactly("onReducerError");
      verify(events, never()).storeSnapshot(any(), any());
    }

    @Test
    void aReducerThatDoesNotAcceptTheArgumentsIsAReducerError() {
      config.globalErrorsHandler = null;
      config.reducers.put("AnEvent", new ReducerMetadata(AnEntity.class, "reducerThatCallsEventMethod"));

      assertThatThrownBy(() -> new EventStore(config).entityReducer(eventEnvelopeFor(someEvent(), "AnEvent"), null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("The reducer AnEntity.reducerThatCallsEventMethod registered for event AnEvent is not a static"
              + " method that accepts (AnEvent, null)");
    }

    @Test
    void aSnapshotOfAnUnregisteredEntityCannotBeReduced() {
      config.entities.clear();

      assertThatThrownBy(() -> new EventStore(config).entityReducer(eventEnvelopeFor(someEvent(), "AnEvent"),
          snapshotFor("42", 1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("No entity registered with the name AnEntity: cannot create an instance of its snapshot");
    }
  }

  @Nested
  class StoreSnapshot {

    @Test
    void storesTheSnapshotInTheEventStore() {
      NonPersistedEntitySnapshotEnvelope someSnapshot = snapshotFor("42", 666);

      new EventStore(config).storeSnapshot(someSnapshot);

      verify(events, times(1)).storeSnapshot(someSnapshot, config);
    }

    @Test
    void logsTheErrorWhenTheSnapshotCannotBeStored() {
      NonPersistedEntitySnapshotEnvelope someSnapshot = snapshotFor("42", 666);
      IllegalStateException someError = new IllegalStateException("some error");
      when(events.storeSnapshot(any(), any())).thenThrow(someError);

      assertThat(new EventStore(config).storeSnapshot(someSnapshot)).isNull();

      verify(logger).error(contains("EventStore#storeSnapshot"),
          contains("The snapshot for entity AnEntity with ID 42 couldn't be stored (Tried on "), same(someSnapshot),
          eq("\nError:"), same(someError));
      verify(logger).error("[Booster]|EventStore#storeSnapshot: ", "[retryWithBackoff] Failed after 3 attempts", someError);
    }
  }

  @Nested
  class StoreDispatchedEvent {

    @Test
    void returnsTheResultOfTheProvider() {
      EventEnvelope event = eventEnvelopeFor(someEvent(), "AnEvent");
      when(events.storeDispatched(event, config)).thenReturn(true, false);

      assertThat(new EventStore(config).storeDispatchedEvent(event)).isTrue();
      assertThat(new EventStore(config).storeDispatchedEvent(event)).isFalse();
      verify(logger, times(2)).debug("[Booster]|EventStore#storeDispatchedEvent: ",
          "Storing event in the dispatched event store:", event);
    }

    @Test
    void processesTheEventWhenTheProviderKeepsFailing() {
      EventEnvelope event = eventEnvelopeFor(someEvent(), "AnEvent");
      IllegalStateException failure = new IllegalStateException("unavailable");
      when(events.storeDispatched(event, config)).thenThrow(failure);

      assertThat(new EventStore(config).storeDispatchedEvent(event)).isTrue();

      verify(events, times(3)).storeDispatched(event, config);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("error", failure);
      details.put("eventEnvelope", event);
      verify(logger).debug("[Booster]|EventStore#storeDispatchedEvent: ",
          "Could not store dispatched event. Continue its processing.", details);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Private methods (package-private in Java)
  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class Steps {

    @Test
    void loadLatestSnapshotLooksForTheLatestSnapshotStoredInTheEventStream() {
      new EventStore(config).loadLatestSnapshot("AnEntity", UUID.of("42"));

      verify(events, times(1)).latestEntitySnapshot(config, "AnEntity", UUID.of("42"));
    }

    @Test
    void loadEventStreamSinceLoadsTheEventStreamFromATimestamp() {
      new EventStore(config).loadEventStreamSince("AnEntity", UUID.of("42"), ORIGIN_OF_TIME);

      verify(events, times(1)).forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME);
    }

    @Test
    void entityReducerComputesTheNewSnapshotWithTheReducerOfTheEventGivenASnapshot() throws Exception {
      EventStore eventStore = storeSpy();
      NonPersistedEntitySnapshotEnvelope snapshot = snapshotEnvelopeFor(someEntity());
      String fakeTime = "2026-11-10T10:00:00.123Z";
      EventEnvelope eventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent", fakeTime);
      Method fakeReducer = FakeReducers.class.getMethod("fakeReducer", Object.class, Object.class);
      doReturn(fakeReducer).when(eventStore).reducerForEvent(any(), any(), any());

      NonPersistedEntitySnapshotEnvelope newSnapshot = eventStore.entityReducer(eventEnvelope, snapshot);

      AnEvent eventInstance = new AnEvent(UUID.of("1"), "42", 1);
      AnEntity entityInstance = new AnEntity(UUID.of("42"), 0);
      verify(eventStore, times(1)).reducerForEvent("AnEvent", eventInstance, entityInstance);
      assertThat(FAKE_REDUCER_CALLS).containsExactly(List.of(eventInstance, entityInstance));
      assertThat(newSnapshot).isEqualTo(NonPersistedEntitySnapshotEnvelope.builder()
          .version(1)
          .superKind(SuperKindType.DOMAIN)
          .requestID(eventEnvelope.requestID())
          .entityID(UUID.of("42"))
          .entityTypeName("AnEntity")
          .typeName("AnEntity")
          .value(Map.of("id", "42", "count", 1))
          .snapshottedEventCreatedAt(fakeTime)
          .build());
    }

    @Test
    void entityReducerGeneratesTheSnapshotWithTheReducerOfTheEventGivenNoSnapshot() throws Exception {
      EventStore eventStore = storeSpy();
      String fakeTime = "2026-11-10T10:00:00.123Z";
      EventEnvelope eventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent", fakeTime);
      Method fakeReducer = FakeReducers.class.getMethod("fakeReducer", Object.class, Object.class);
      doReturn(fakeReducer).when(eventStore).reducerForEvent(any(), any(), any());

      NonPersistedEntitySnapshotEnvelope newSnapshot = eventStore.entityReducer(eventEnvelope, null);

      AnEvent eventInstance = new AnEvent(UUID.of("1"), "42", 1);
      verify(eventStore, times(1)).reducerForEvent("AnEvent", eventInstance, null);
      assertThat(FAKE_REDUCER_CALLS).containsExactly(Arrays.asList(eventInstance, null));
      assertThat(newSnapshot).isEqualTo(NonPersistedEntitySnapshotEnvelope.builder()
          .version(1)
          .superKind(SuperKindType.DOMAIN)
          .requestID(eventEnvelope.requestID())
          .entityID(UUID.of("42"))
          .entityTypeName("AnEntity")
          .typeName("AnEntity")
          .value(Map.of("id", "42", "count", 1))
          .snapshottedEventCreatedAt(fakeTime)
          .build());
    }

    @Test
    void entityReducerCalculatesTheNewInternalSnapshotOfAnInternalEvent() {
      String fakeTime = "2026-11-10T10:00:00.123Z";
      EventEnvelope eventEnvelope = EventEnvelope.builder()
          .version(1)
          .entityID(UUID.of("42"))
          .entityTypeName("AnEntity")
          .value(rawMap("oldEntityName", "oldEntityName", "oldEntityId", "oldEntityId", "newEntityName", "newEntityName",
              "newEntity", rawMap("id", "42")))
          .requestID(UUID.of("whatever"))
          .typeName(BoosterEntityMigrated.class.getSimpleName())
          .superKind(SuperKindType.BOOSTER)
          .createdAt(fakeTime)
          .build();

      NonPersistedEntitySnapshotEnvelope newSnapshot =
          new EventStore(config).entityReducer(eventEnvelope, snapshotFor("42", 1));

      assertThat(newSnapshot).isEqualTo(NonPersistedEntitySnapshotEnvelope.builder()
          .version(1)
          .requestID(eventEnvelope.requestID())
          .entityID(UUID.of("42"))
          .entityTypeName("newEntityName")
          .typeName("newEntityName")
          .superKind(SuperKindType.BOOSTER)
          .value(Map.of("id", "42"))
          .snapshottedEventCreatedAt(fakeTime)
          .build());
    }

    @Test
    void aReducerCanCallAnInstanceMethodOfTheEvent() {
      EventEnvelope eventEnvelope = eventEnvelopeFor(someEvent(), "AnotherEvent");

      new EventStore(config).entityReducer(eventEnvelope, null);

      assertThat(GET_PREFIXED_ID_CALLS).hasValue(1);
    }

    @Test
    void aReducerCanCallAnInstanceMethodOfTheEntity() {
      EventEnvelope eventEnvelope = eventEnvelopeFor(someEvent(), "AnEvent");

      new EventStore(config).entityReducer(eventEnvelope, snapshotEnvelopeFor(someEntity()));

      assertThat(GET_ID_CALLS).hasValue(1);
    }

    @Test
    void reducerForEventReturnsTheReducerMethodOfTheEvent() throws Exception {
      Method reducer = new EventStore(config).reducerForEvent("AnEvent", new AnEvent(UUID.of("1"), "42", 1), null);

      assertThat(reducer).isEqualTo(AnEntity.class.getMethod("reducerThatCallsEntityMethod", AnEvent.class, AnEntity.class));
      verify(logger).debug("[Booster]|EventStore#reducerForEvent: ",
          "Found reducer for event AnEvent: \"AnEntity.reducerThatCallsEntityMethod\"");
    }

    @Test
    void reducerForEventFailsWhenNoReducerIsRegistered() {
      AnEvent event = new AnEvent(UUID.of("1"), "42", 1);

      assertThatThrownBy(() -> new EventStore(config).reducerForEvent("Unreduced", event, null))
          .isInstanceOfSatisfying(InvalidReducerError.class, error -> {
            assertThat(error).hasMessage("No reducer registered for event Unreduced");
            assertThat(error.eventInstance()).isSameAs(event);
            assertThat(error.snapshotInstance()).isNull();
          });
    }
  }

  @Nested
  class Tracing {

    @Test
    void tracesTheFetchAndItsSteps() {
      BoosterConfig global = Booster.config();
      List<String> traced = Collections.synchronizedList(new ArrayList<>());
      global.traceConfiguration.enableAll();
      global.traceConfiguration.onStart = (traceConfig, actionType, info) ->
          traced.add(actionType + " " + info.className + "#" + info.methodName);
      global.traceConfiguration.onEnd = (traceConfig, actionType, info) -> traced.add("end " + info.methodName);
      when(events.forEntitySince(config, "AnEntity", UUID.of("42"), ORIGIN_OF_TIME))
          .thenReturn(List.of(eventEnvelopeFor(someEvent(), "AnEvent")));

      new EventStore(config).fetchEntitySnapshot("AnEntity", UUID.of("42"));

      assertThat(traced).containsExactly(
          TraceActionTypes.FETCH_ENTITY_SNAPSHOT.value() + " EventStore#fetchEntitySnapshot",
          TraceActionTypes.LOAD_LATEST_SNAPSHOT.value() + " EventStore#loadLatestSnapshot",
          "end loadLatestSnapshot",
          TraceActionTypes.LOAD_EVENT_STREAM_SINCE.value() + " EventStore#loadEventStreamSince",
          "end loadEventStreamSince",
          TraceActionTypes.ENTITY_REDUCER.value() + " EventStore#entityReducer",
          TraceActionTypes.SCHEMA_MIGRATOR_MIGRATE.value() + " SchemaMigrator#migrate",
          "end migrate",
          "end entityReducer",
          TraceActionTypes.STORE_SNAPSHOT.value() + " EventStore#storeSnapshot",
          "end storeSnapshot",
          "end fetchEntitySnapshot");
    }

    @Test
    void tracesStoreDispatchedEventAsCustom() {
      BoosterConfig global = Booster.config();
      List<TraceInfo> traced = Collections.synchronizedList(new ArrayList<>());
      global.traceConfiguration.enableFor(TraceActionTypes.CUSTOM.value());
      global.traceConfiguration.onStart = (traceConfig, actionType, info) -> traced.add(info);
      EventEnvelope event = eventEnvelopeFor(someEvent(), "AnEvent");

      new EventStore(config).storeDispatchedEvent(event);

      assertThat(traced).singleElement().satisfies(info -> {
        assertThat(info.className).isEqualTo("EventStore");
        assertThat(info.methodName).isEqualTo("storeDispatchedEvent");
        assertThat(info.args).containsExactly(event);
      });
    }
  }

  @Test
  void theOriginOfTimeIsTheJavaScriptEpochString() {
    assertThat(EventStore.ORIGIN_OF_TIME).isEqualTo(ORIGIN_OF_TIME);
    assertThat(EventStore.BOOSTER_REDUCIBLE_EVENTS_TYPES_NAMES).containsExactly("BoosterEntityMigrated", "BoosterEntityTouched");
  }
}
