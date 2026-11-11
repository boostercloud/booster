package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Retrier;
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
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The events part of the provider: reading an entity's events and latest snapshot, storing events (and dispatching
 * them in-process) and snapshots. Port of {@code library/events-adapter.ts}, plus the Java-only crash recovery of
 * undispatched events ({@link #recoverUndispatchedEvents}).
 */
public final class EventsAdapter {

  /** {@code originOfTime}: {@code new Date(0).toISOString()}. */
  public static final String ORIGIN_OF_TIME = Dates.toIso(0L);

  private EventsAdapter() {}

  /**
   * {@code rawEventsToEnvelopes(rawEvents)}: the events handed to {@code boosterEventDispatcher}, as envelopes
   * (envelope records are kept as they are, raw documents are converted).
   *
   * @param rawEvents a list of events
   * @return the envelopes
   */
  public static List<EventEnvelope> rawEventsToEnvelopes(Object rawEvents) {
    if (!(rawEvents instanceof List<?> events)) {
      throw new IllegalArgumentException("rawEvents.map is not a function");
    }
    List<EventEnvelope> envelopes = new ArrayList<>(events.size());
    for (Object event : events) {
      if (event instanceof EventEnvelope envelope) {
        envelopes.add(envelope);
      } else if (event instanceof NonPersistedEventEnvelope nonPersisted) {
        envelopes.add(nonPersisted.persisted(null));
      } else {
        envelopes.add(Envelopes.toEventEnvelope(event instanceof Map<?, ?> ? event : BoosterJson.toRawMap(event)));
      }
    }
    return envelopes;
  }

  /**
   * {@code readEntityEventsSince(eventRegistry, config, entityTypeName, entityID, since?)}: the non-deleted events of
   * an entity created strictly after {@code since}, oldest first.
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param entityTypeName the entity name
   * @param entityID the entity id
   * @param since ISO timestamp; {@code null} or empty means the origin of time
   * @return the events (their database {@code _id} as {@code id})
   */
  public static List<EventEnvelope> readEntityEventsSince(EventRegistry eventRegistry, BoosterConfig config,
      String entityTypeName, UUID entityID, String since) {
    Logger logger = Loggers.getLogger(config, "events-adapter#readEntityEventsSince");
    String fromTime = since != null && !since.isEmpty() ? since : ORIGIN_OF_TIME;
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("entityID", entityID == null ? null : entityID.value());
    query.put("entityTypeName", entityTypeName);
    query.put("kind", "event");
    query.put("createdAt", Map.of("$gt", fromTime));
    query.put("deletedAt", Map.of("$exists", false));
    List<EventEnvelope> result = Envelopes.toEventEnvelopes(eventRegistry.query(query));
    logger.debug("Loaded events for entity " + entityTypeName + " with ID " + entityID + " with result:", result);
    return result;
  }

  /**
   * {@code readEntityLatestSnapshot(eventRegistry, config, entityTypeName, entityID)}: the snapshot with the greatest
   * {@code snapshottedEventCreatedAt}.
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param entityTypeName the entity name
   * @param entityID the entity id
   * @return the snapshot, if any
   */
  public static Optional<EntitySnapshotEnvelope> readEntityLatestSnapshot(EventRegistry eventRegistry,
      BoosterConfig config, String entityTypeName, UUID entityID) {
    Logger logger = Loggers.getLogger(config, "events-adapter#readEntityLatestSnapshot");
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("entityID", entityID == null ? null : entityID.value());
    query.put("entityTypeName", entityTypeName);
    query.put("kind", "snapshot");
    Map<String, Object> snapshot = eventRegistry.queryLatestSnapshot(query);
    if (snapshot != null) {
      EntitySnapshotEnvelope envelope = Envelopes.toSnapshotEnvelope(snapshot);
      logger.debug("Snapshot found for entity " + entityTypeName + " with ID " + entityID + ":", envelope);
      return Optional.of(envelope);
    }
    logger.debug("No snapshot found for entity " + entityTypeName + " with ID " + entityID + ".");
    return Optional.empty();
  }

  /**
   * {@code storeEvents(userApp, eventRegistry, nonPersistedEventEnvelopes, config)}: stores the events one by one, in
   * order, each with its own {@code createdAt}, then hands the whole batch (even an empty one) to
   * {@code userApp.boosterEventDispatcher} and waits for it. If storing an event fails, the previous ones stay stored,
   * nothing is dispatched and the error propagates. This is the TypeScript behaviour, without crash recovery.
   *
   * @param userApp the application
   * @param eventRegistry the registry
   * @param nonPersistedEventEnvelopes the events
   * @param config the configuration
   * @return the stored events
   */
  public static List<EventEnvelope> storeEvents(UserApp userApp, EventRegistry eventRegistry,
      List<NonPersistedEventEnvelope> nonPersistedEventEnvelopes, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-adapter#storeEvents");
    logger.debug("Storing the following event envelopes:", nonPersistedEventEnvelopes);
    List<EventEnvelope> persistedEventEnvelopes = new ArrayList<>();
    for (NonPersistedEventEnvelope nonPersistedEventEnvelope : nonPersistedEventEnvelopes) {
      EventEnvelope persistableEventEnvelope = nonPersistedEventEnvelope.persisted(Dates.nowIso());
      Map<String, Object> document = Envelopes.toDocument(persistableEventEnvelope);
      Retrier.retryIfError(tryNumber -> {
        persistEvent(eventRegistry, document);
        return null;
      }, OptimisticConcurrencyUnexpectedVersionError.class);
      persistedEventEnvelopes.add(persistableEventEnvelope);
    }
    logger.debug("EventEnvelopes stored: ", persistedEventEnvelopes);
    userApp.boosterEventDispatcher(persistedEventEnvelopes);
    return persistedEventEnvelopes;
  }

  /**
   * {@link #storeEvents(UserApp, EventRegistry, List, BoosterConfig)} with crash recovery: before anything is stored,
   * the batch is recorded as pending in the dispatch journal (one flushed write), each event gets its database id up
   * front (returned as {@code id}, so that {@code storeDispatched} can recognise it), and the journal entries are
   * removed once the dispatch returns or fails. A crash in between leaves the entries behind for
   * {@link #recoverUndispatchedEvents}.
   *
   * <p>The events of the batch (the events one command, event handler or scheduled command registered) are stored
   * atomically, with one flushed append framed as an atomic batch ({@link EventRegistry#storeAll}): a failed write is
   * truncated away and a crash that cuts the append is discarded as a whole on the next load, so all of them are
   * stored or none, never part of the batch. Each still gets its own {@code createdAt}, in order.
   *
   * @param userApp the application
   * @param eventRegistry the registry
   * @param journal the dispatch journal
   * @param nonPersistedEventEnvelopes the events
   * @param config the configuration
   * @return the stored events, with their {@code id}
   */
  public static List<EventEnvelope> storeEvents(UserApp userApp, EventRegistry eventRegistry, DispatchJournal journal,
      List<NonPersistedEventEnvelope> nonPersistedEventEnvelopes, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-adapter#storeEvents");
    logger.debug("Storing the following event envelopes:", nonPersistedEventEnvelopes);
    List<String> ids = new ArrayList<>(nonPersistedEventEnvelopes.size());
    List<Map<String, Object>> entries = new ArrayList<>(nonPersistedEventEnvelopes.size());
    for (NonPersistedEventEnvelope envelope : nonPersistedEventEnvelopes) {
      String id = eventRegistry.createNewId();
      ids.add(id);
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("_id", id);
      entry.put("entityTypeName", envelope.entityTypeName());
      entry.put("entityID", envelope.entityID() == null ? null : envelope.entityID().value());
      entries.add(entry);
    }
    journal.addPending(entries);
    try {
      List<EventEnvelope> persistedEventEnvelopes = new ArrayList<>();
      List<Map<String, Object>> documents = new ArrayList<>(nonPersistedEventEnvelopes.size());
      for (int i = 0; i < nonPersistedEventEnvelopes.size(); i++) {
        EventEnvelope persisted = nonPersistedEventEnvelopes.get(i).persisted(Dates.nowIso());
        Map<String, Object> document = Envelopes.toDocument(persisted);
        document.put("_id", ids.get(i));
        documents.add(document);
        persistedEventEnvelopes.add(persisted.toBuilder().id(ids.get(i)).build());
      }
      Retrier.retryIfError(tryNumber -> {
        eventRegistry.storeAll(documents);
        return null;
      }, OptimisticConcurrencyUnexpectedVersionError.class);
      logger.debug("EventEnvelopes stored: ", persistedEventEnvelopes);
      userApp.boosterEventDispatcher(persistedEventEnvelopes);
      return persistedEventEnvelopes;
    } finally {
      journal.remove(ids);
    }
  }

  /**
   * {@code storeSnapshot(eventRegistry, snapshotEnvelope, config)}: inserts the snapshot with
   * {@code createdAt = snapshottedEventCreatedAt} and {@code persistedAt = now} (snapshots accumulate).
   *
   * @param eventRegistry the registry
   * @param snapshotEnvelope the snapshot
   * @param config the configuration
   * @return the stored snapshot
   */
  public static EntitySnapshotEnvelope storeSnapshot(EventRegistry eventRegistry,
      NonPersistedEntitySnapshotEnvelope snapshotEnvelope, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-adapter#storeSnapshot");
    logger.debug("Storing the following snapshot envelope:", snapshotEnvelope);
    EntitySnapshotEnvelope persistableEntitySnapshot = snapshotEnvelope.persisted(Dates.nowIso());
    Map<String, Object> document = Envelopes.toDocument(persistableEntitySnapshot);
    Retrier.retryIfError(tryNumber -> {
      eventRegistry.store(document);
      return null;
    }, OptimisticConcurrencyUnexpectedVersionError.class);
    logger.debug("Snapshot stored");
    return persistableEntitySnapshot;
  }

  /**
   * {@code storeDispatchedEvent()}: TypeScript's local provider does not track dispatched events and always answers
   * {@code true}.
   *
   * @return {@code true}
   */
  public static boolean storeDispatchedEvent() {
    return true;
  }

  /**
   * Java-only dedup check behind {@code events.storeDispatched}: {@code false} when the event was already handed to
   * core (only possible when it is re-dispatched by {@link #recoverUndispatchedEvents}), {@code true} otherwise.
   * Events without an {@code id} or without a journal entry are always {@code true}, as in TypeScript.
   *
   * @param journal the dispatch journal
   * @param eventEnvelope the event core is about to process
   * @param config the configuration
   * @return whether core must process the event
   */
  public static boolean storeDispatchedEvent(DispatchJournal journal, EventEnvelope eventEnvelope,
      BoosterConfig config) {
    if (eventEnvelope == null || eventEnvelope.id() == null) {
      return true;
    }
    return journal.markDispatched(eventEnvelope.id());
  }

  /**
   * Java-only: re-dispatches, through {@code userApp.boosterEventDispatcher}, every stored event whose dispatch had
   * not started when the process stopped (its journal entry is still pending). Entries whose event was never stored
   * are dropped, and so are entries whose processing had started (core's at-most-once rule after
   * {@code storeDispatched}). Events are re-dispatched in one batch, oldest first. When the dispatch fails the pending
   * entries are kept for the next recovery and the error propagates. Dispatches running in this process are never
   * touched, so calling this while the application serves requests is safe.
   *
   * @param userApp the application
   * @param eventRegistry the registry
   * @param journal the dispatch journal
   * @param config the configuration
   * @return the re-dispatched events
   */
  public static List<EventEnvelope> recoverUndispatchedEvents(UserApp userApp, EventRegistry eventRegistry,
      DispatchJournal journal, BoosterConfig config) {
    Logger logger = Loggers.getLogger(config, "events-adapter#recoverUndispatchedEvents");
    List<String> interrupted = idsOf(journal.interruptedEntries());
    if (!interrupted.isEmpty()) {
      logger.warn("The processing of " + interrupted.size() + " event(s) was interrupted by the previous shutdown; "
          + "core had already marked them as dispatched, so they are not dispatched again:", interrupted);
      journal.removeDispatched(interrupted);
    }
    List<String> pending = idsOf(journal.pendingEntries());
    if (pending.isEmpty()) {
      logger.debug("No undispatched events to recover");
      return List.of();
    }
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("_id", Map.of("$in", new ArrayList<Object>(pending)));
    List<EventEnvelope> events = new ArrayList<>();
    Set<String> found = new LinkedHashSet<>();
    for (Map<String, Object> document : eventRegistry.query(query)) {
      String id = String.valueOf(document.get("_id"));
      found.add(id);
      if ("event".equals(document.get("kind")) && !document.containsKey("deletedAt")) {
        events.add(Envelopes.toEventEnvelope(document));
      }
    }
    List<String> discarded = new ArrayList<>();
    for (String id : pending) {
      if (!found.contains(id) || events.stream().noneMatch(event -> id.equals(event.id()))) {
        discarded.add(id);
      }
    }
    if (!discarded.isEmpty()) {
      logger.debug("Discarding journal entries of events that were never stored:", discarded);
      journal.remove(discarded);
    }
    if (events.isEmpty()) {
      return List.of();
    }
    List<String> eventIds = events.stream().map(EventEnvelope::id).toList();
    journal.track(eventIds);
    logger.info("Re-dispatching " + events.size() + " event(s) that were stored but not dispatched before the "
        + "previous shutdown:", events);
    try {
      userApp.boosterEventDispatcher(events);
    } catch (RuntimeException | Error e) {
      logger.error("Could not re-dispatch the recovered events; they will be retried on the next recovery", e);
      journal.removeDispatched(eventIds);
      throw e;
    }
    journal.remove(eventIds);
    return events;
  }

  private static List<String> idsOf(List<Map<String, Object>> entries) {
    List<String> ids = new ArrayList<>(entries.size());
    for (Map<String, Object> entry : entries) {
      ids.add(String.valueOf(entry.get("_id")));
    }
    return ids;
  }

  private static void persistEvent(EventRegistry eventRegistry, Map<String, Object> eventEnvelope) {
    eventRegistry.store(eventEnvelope);
  }
}
