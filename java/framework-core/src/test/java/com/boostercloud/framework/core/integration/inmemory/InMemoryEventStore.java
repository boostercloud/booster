package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.PaginatedEntityIdResult;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The event store of the in-memory provider: events and snapshots kept as raw JSON documents (the shape a database
 * returns, so the core binds every value from raw data), with the query semantics of the local provider (events
 * since a date in creation order, latest snapshot by {@code snapshottedEventCreatedAt}, searches newest first,
 * soft-deleted events).
 *
 * <p>Stored events are dispatched like a change feed: {@link #store} appends the page to a queue and, unless a
 * dispatch is already running, drains it by calling {@code boosterEventDispatcher} page after page on the calling
 * thread. Events stored while a page is being dispatched (by event handlers, data migrations...) are therefore
 * dispatched after it, and the call that stored the first page returns when everything has been processed. With
 * {@link #autoDispatch(boolean) autoDispatch(false)} nothing is dispatched; {@link #dispatch(List)} dispatches a page
 * explicitly.
 */
public final class InMemoryEventStore implements ProviderEventsLibrary {

  private static final String ORIGIN_OF_TIME = "1970-01-01T00:00:00.000Z";

  private final List<Map<String, Object>> entries = new ArrayList<>();
  private final Set<String> dispatched = new HashSet<>();
  private final List<EventEnvelope> produced = new ArrayList<>();
  private final Deque<List<EventEnvelope>> pendingPages = new ArrayDeque<>();
  private final Object dispatchLock = new Object();
  private boolean dispatching;
  private long lastCreatedAtMillis;
  private volatile boolean autoDispatch = true;
  private volatile int snapshotsStored;

  /**
   * @param enabled whether stored events are dispatched
   */
  public void autoDispatch(boolean enabled) {
    autoDispatch = enabled;
  }

  /**
   * Dispatches a page of events now (what the change feed delivers), then whatever it stores.
   *
   * @param page the events
   */
  public void dispatch(List<EventEnvelope> page) {
    enqueueAndDrain(page);
  }

  /**
   * @return every stored event, oldest first, bound back from storage
   */
  public synchronized List<EventEnvelope> events() {
    return entries.stream().filter(kind(EventEnvelope.KIND)).map(InMemoryEventStore::toEvent).toList();
  }

  /**
   * @return every stored snapshot, in storage order
   */
  public synchronized List<EntitySnapshotEnvelope> snapshots() {
    return entries.stream().filter(kind(EntitySnapshotEnvelope.KIND)).map(InMemoryEventStore::toSnapshot).toList();
  }

  /**
   * @return how many snapshots were stored
   */
  public int snapshotsStored() {
    return snapshotsStored;
  }

  /**
   * @return the events handed to {@link #produce}, in order
   */
  public synchronized List<EventEnvelope> produced() {
    return List.copyOf(produced);
  }

  /**
   * Stores an event as it is (tests use it to plant events written by an older version of the application), without
   * dispatching it.
   *
   * @param event the persisted event
   */
  public synchronized void plant(EventEnvelope event) {
    entries.add(raw(event, EventEnvelope.KIND));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // ProviderEventsLibrary
  // ---------------------------------------------------------------------------------------------------------------

  @Override
  @SuppressWarnings("unchecked")
  public List<EventEnvelope> rawToEnvelopes(Object rawEvents) {
    return rawEvents == null ? List.of() : List.copyOf((List<EventEnvelope>) rawEvents);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<EventEnvelope> rawStreamToEnvelopes(BoosterConfig config, Object context, List<Object> dedupEventStream) {
    List<EventEnvelope> envelopes = new ArrayList<>();
    for (Object message : dedupEventStream) {
      envelopes.add(BoosterJson.convert(message, EventEnvelope.class));
    }
    return envelopes;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Object> dedupEventStream(BoosterConfig config, Object rawEvents) {
    List<Object> unique = new ArrayList<>();
    for (Object message : (List<Object>) rawEvents) {
      Object id = message instanceof Map<?, ?> map ? map.get("id") : null;
      synchronized (this) {
        if (id == null || dispatched.add("stream:" + id)) {
          unique.add(message);
        }
      }
    }
    return unique;
  }

  @Override
  public synchronized void produce(String entityName, UUID entityID, List<EventEnvelope> eventEnvelopes,
      BoosterConfig config) {
    produced.addAll(eventEnvelopes);
  }

  @Override
  public synchronized List<EventEnvelope> forEntitySince(BoosterConfig config, String entityTypeName, UUID entityID,
      String since) {
    String fromTime = since == null ? ORIGIN_OF_TIME : since;
    return entries.stream()
        .filter(kind(EventEnvelope.KIND))
        .filter(of(entityTypeName, entityID))
        .filter(entry -> createdAt(entry).compareTo(fromTime) > 0)
        .filter(entry -> entry.get("deletedAt") == null)
        .sorted(Comparator.comparing(InMemoryEventStore::createdAt))
        .map(InMemoryEventStore::toEvent)
        .toList();
  }

  @Override
  public synchronized Optional<EntitySnapshotEnvelope> latestEntitySnapshot(BoosterConfig config,
      String entityTypeName, UUID entityID) {
    return entries.stream()
        .filter(kind(EntitySnapshotEnvelope.KIND))
        .filter(of(entityTypeName, entityID))
        .max(Comparator.comparing(entry -> String.valueOf(entry.get("snapshottedEventCreatedAt"))))
        .map(InMemoryEventStore::toSnapshot);
  }

  @Override
  public synchronized List<EventSearchResponse> search(BoosterConfig config, EventSearchParameters parameters) {
    Predicate<Map<String, Object>> filter = switch (parameters) {
      case EventParametersFilterByEntity byEntity -> entry -> byEntity.entity().equals(entry.get("entityTypeName"))
          && (byEntity.entityID() == null || byEntity.entityID().equals(String.valueOf(entry.get("entityID"))));
      case EventParametersFilterByType byType -> entry -> byType.type().equals(entry.get("typeName"));
    };
    List<EventSearchResponse> responses = entries.stream()
        .filter(kind(EventEnvelope.KIND))
        .filter(filter)
        .filter(entry -> parameters.from() == null || createdAt(entry).compareTo(parameters.from()) >= 0)
        .filter(entry -> parameters.to() == null || createdAt(entry).compareTo(parameters.to()) <= 0)
        .sorted(Comparator.comparing(InMemoryEventStore::createdAt).reversed())
        .map(entry -> EventSearchResponse.fromEventEnvelope(toEvent(entry)))
        .toList();
    Integer limit = parameters.limit();
    return limit != null && limit > 0 && responses.size() > limit ? responses.subList(0, limit) : responses;
  }

  @Override
  public synchronized PaginatedEntitiesIdsResult searchEntitiesIDs(BoosterConfig config, int limit,
      Map<String, String> afterCursor, String entityTypeName) {
    int skip = afterCursor != null && afterCursor.get("id") != null ? Integer.parseInt(afterCursor.get("id")) : 0;
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    entries.stream()
        .filter(kind(EventEnvelope.KIND))
        .filter(entry -> entityTypeName.equals(entry.get("entityTypeName")))
        .filter(entry -> entry.get("deletedAt") == null)
        .sorted(Comparator.comparing(InMemoryEventStore::createdAt).reversed())
        .forEach(entry -> ids.add(String.valueOf(entry.get("entityID"))));
    List<PaginatedEntityIdResult> page = ids.stream().skip(skip).limit(limit)
        .map(id -> new PaginatedEntityIdResult(UUID.of(id)))
        .toList();
    Map<String, String> cursor = Map.of("id", String.valueOf((limit > 0 ? limit : 1) + skip));
    return new PaginatedEntitiesIdsResult(page, page.size(), cursor);
  }

  @Override
  public List<EventEnvelope> store(List<NonPersistedEventEnvelope> eventEnvelopes, BoosterConfig config) {
    List<EventEnvelope> persisted = new ArrayList<>();
    synchronized (this) {
      for (NonPersistedEventEnvelope envelope : eventEnvelopes) {
        EventEnvelope event = envelope.persisted(nextCreatedAt()).toBuilder().id(UUID.generate().value()).build();
        Map<String, Object> entry = raw(event, EventEnvelope.KIND);
        entries.add(entry);
        persisted.add(toEvent(entry));
      }
    }
    if (autoDispatch && !persisted.isEmpty()) {
      enqueueAndDrain(persisted);
    }
    return persisted;
  }

  @Override
  public EntitySnapshotEnvelope storeSnapshot(NonPersistedEntitySnapshotEnvelope snapshotEnvelope,
      BoosterConfig config) {
    EntitySnapshotEnvelope snapshot =
        snapshotEnvelope.persisted(Dates.nowIso()).toBuilder().id(UUID.generate().value()).build();
    synchronized (this) {
      entries.add(raw(snapshot, EntitySnapshotEnvelope.KIND));
      snapshotsStored++;
    }
    return snapshot;
  }

  @Override
  public synchronized boolean storeDispatched(EventEnvelope eventEnvelope, BoosterConfig config) {
    return dispatched.add(Objects.requireNonNull(eventEnvelope.id(), "event id"));
  }

  @Override
  public synchronized List<EventEnvelope> findDeletableEvent(BoosterConfig config, EventDeleteParameters parameters) {
    return entries.stream()
        .filter(kind(EventEnvelope.KIND))
        .filter(of(parameters.entityTypeName(), parameters.entityID()))
        .filter(entry -> parameters.createdAt().equals(entry.get("createdAt")))
        .filter(entry -> entry.get("deletedAt") == null)
        .map(InMemoryEventStore::toEvent)
        .toList();
  }

  @Override
  public synchronized List<EntitySnapshotEnvelope> findDeletableSnapshot(BoosterConfig config,
      SnapshotDeleteParameters parameters) {
    return entries.stream()
        .filter(kind(EntitySnapshotEnvelope.KIND))
        .filter(of(parameters.entityTypeName(), parameters.entityID()))
        .filter(entry -> parameters.createdAt().equals(entry.get("createdAt")))
        .map(InMemoryEventStore::toSnapshot)
        .toList();
  }

  @Override
  public synchronized void deleteEvent(BoosterConfig config, List<EventEnvelope> events) {
    for (EventEnvelope event : events) {
      for (Map<String, Object> entry : entries) {
        if (event.id().equals(entry.get("id"))) {
          entry.put("deletedAt", Dates.nowIso());
          entry.put("value", new LinkedHashMap<>());
        }
      }
    }
  }

  @Override
  public synchronized void deleteSnapshot(BoosterConfig config, List<EntitySnapshotEnvelope> snapshots) {
    for (EntitySnapshotEnvelope snapshot : snapshots) {
      entries.removeIf(entry -> snapshot.id().equals(entry.get("id")));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Dispatching
  // ---------------------------------------------------------------------------------------------------------------

  private void enqueueAndDrain(List<EventEnvelope> page) {
    synchronized (dispatchLock) {
      pendingPages.add(page);
      if (dispatching) {
        return;
      }
      dispatching = true;
    }
    boolean drained = false;
    try {
      while (true) {
        List<EventEnvelope> next;
        synchronized (dispatchLock) {
          next = pendingPages.poll();
          if (next == null) {
            dispatching = false;
            drained = true;
            return;
          }
        }
        UserAppRegistry.get().boosterEventDispatcher(next);
      }
    } finally {
      if (!drained) {
        synchronized (dispatchLock) {
          dispatching = false;
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Raw documents
  // ---------------------------------------------------------------------------------------------------------------

  private String nextCreatedAt() {
    long millis = Math.max(Dates.nowEpochMillis(), lastCreatedAtMillis + 1);
    lastCreatedAtMillis = millis;
    return Dates.toIso(millis);
  }

  private static Map<String, Object> raw(Object envelope, String kind) {
    Map<String, Object> entry = new LinkedHashMap<>(BoosterJson.toRawMap(envelope));
    entry.put("kind", kind);
    return entry;
  }

  private static EventEnvelope toEvent(Map<String, Object> entry) {
    return BoosterJson.convert(BoosterJson.toRaw(entry), EventEnvelope.class);
  }

  private static EntitySnapshotEnvelope toSnapshot(Map<String, Object> entry) {
    return BoosterJson.convert(BoosterJson.toRaw(entry), EntitySnapshotEnvelope.class);
  }

  private static String createdAt(Map<String, Object> entry) {
    return String.valueOf(entry.get("createdAt"));
  }

  private static Predicate<Map<String, Object>> kind(String kind) {
    return entry -> kind.equals(entry.get("kind"));
  }

  private static Predicate<Map<String, Object>> of(String entityTypeName, UUID entityID) {
    return entry -> Objects.equals(entityTypeName, entry.get("entityTypeName"))
        && entityID != null && entityID.value().equals(String.valueOf(entry.get("entityID")));
  }
}
