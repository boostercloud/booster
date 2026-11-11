package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.DatastoreOptions;
import com.boostercloud.framework.provider.local.store.UpdateOptions;
import com.boostercloud.framework.provider.local.store.UpdateResult;
import com.boostercloud.framework.types.Dates;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java-only: the durable record of events that were stored but whose dispatch has not finished
 * ({@code .booster/dispatch_journal.json}, NeDB format). It closes the gap of the TypeScript provider, which
 * dispatches events in-process right after storing them, so a crash between the two loses the dispatch (and the
 * read-model projection).
 *
 * <p>Each entry is {@code { _id: <event _id>, entityTypeName, entityID, state, storedAt }} where {@code state} is
 *
 * <ul>
 *   <li>{@value #PENDING}: written (and flushed) <em>before</em> the event itself is stored;
 *   <li>{@value #DISPATCHED}: core started processing the event ({@code events.storeDispatched} was called; from then
 *       on core's at-most-once rule applies and the event is never dispatched again).
 * </ul>
 *
 * Entries are removed once the dispatch call returns. After a crash, {@link #pendingEntries()} are the events to
 * re-dispatch; entries whose event was never stored (the crash happened before the insert) are dropped.
 *
 * <p>Entries of dispatches running in this process are tracked in memory ({@link #isInFlight(String)}) so that a
 * recovery run concurrently with normal traffic (the websocket server already serves while the server starts) never
 * re-dispatches them, not even one that finishes while the recovery reads the journal.
 */
public class DispatchJournal {

  /** State of an entry whose event has not been handed to core yet. */
  public static final String PENDING = "pending";

  /** State of an entry whose event core has started to process. */
  public static final String DISPATCHED = "dispatched";

  /** Obsolete lines after which the journal compacts itself (it is rewritten on every load as well). */
  public static final int AUTOCOMPACTION_LINE_THRESHOLD = 1000;

  private final Datastore journal;
  private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

  /** A journal on {@code .booster/dispatch_journal.json} in the working directory. */
  public DispatchJournal() {
    this(Paths.resolve(null, Paths.DISPATCH_JOURNAL_DATABASE));
  }

  /**
   * @param file the data file
   */
  public DispatchJournal(Path file) {
    this(new Datastore(DatastoreOptions.of(file).withAutocompactionLineThreshold(AUTOCOMPACTION_LINE_THRESHOLD)));
  }

  /**
   * @param datastore the collection to use
   */
  public DispatchJournal(Datastore datastore) {
    this.journal = Objects.requireNonNull(datastore, "datastore");
  }

  /**
   * @return the underlying collection
   */
  public Datastore datastore() {
    return journal;
  }

  /**
   * Records, durably, that these events are about to be stored and dispatched.
   *
   * @param entries one map per event: {@code _id}, {@code entityTypeName}, {@code entityID}
   */
  public void addPending(List<Map<String, Object>> entries) {
    if (entries.isEmpty()) {
      return;
    }
    String storedAt = Dates.nowIso();
    List<Map<String, Object>> documents = new ArrayList<>(entries.size());
    for (Map<String, Object> entry : entries) {
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("_id", entry.get("_id"));
      document.put("entityTypeName", entry.get("entityTypeName"));
      document.put("entityID", entry.get("entityID"));
      document.put("state", PENDING);
      document.put("storedAt", storedAt);
      documents.add(document);
      inFlight.add(String.valueOf(entry.get("_id")));
    }
    try {
      journal.insert(documents);
    } catch (RuntimeException e) {
      for (Map<String, Object> entry : entries) {
        inFlight.remove(String.valueOf(entry.get("_id")));
      }
      throw e;
    }
  }

  /**
   * The dedup check behind {@code events.storeDispatched}: moves a pending entry to {@value #DISPATCHED}.
   *
   * @param eventId the event {@code _id}
   * @return {@code true} when the event must be processed (its entry was pending, or it has no entry, e.g. events
   *     stored by the TypeScript provider), {@code false} when it was already dispatched
   */
  public boolean markDispatched(String eventId) {
    Map<String, Object> pending = new LinkedHashMap<>();
    pending.put("_id", eventId);
    pending.put("state", PENDING);
    UpdateResult result = journal.update(pending, Map.of("$set", Map.of("state", DISPATCHED)), UpdateOptions.DEFAULTS);
    if (result.numAffected() > 0) {
      return true;
    }
    return journal.count(Map.of("_id", eventId)) == 0;
  }

  /**
   * Forgets entries (their dispatch returned).
   *
   * @param eventIds the event ids
   */
  public void remove(Collection<String> eventIds) {
    if (eventIds.isEmpty()) {
      return;
    }
    try {
      journal.remove(Map.of("_id", Map.of("$in", new ArrayList<Object>(eventIds))), true);
    } finally {
      inFlight.removeAll(eventIds);
    }
  }

  /**
   * Forgets the entries of these events that reached the {@value #DISPATCHED} state, keeping the pending ones.
   *
   * @param eventIds the event ids
   */
  public void removeDispatched(Collection<String> eventIds) {
    if (eventIds.isEmpty()) {
      return;
    }
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("_id", Map.of("$in", new ArrayList<Object>(eventIds)));
    query.put("state", DISPATCHED);
    try {
      journal.remove(query, true);
    } finally {
      inFlight.removeAll(eventIds);
    }
  }

  /**
   * Marks entries as being dispatched by this process (recovery).
   *
   * @param eventIds the event ids
   */
  public void track(Collection<String> eventIds) {
    inFlight.addAll(eventIds);
  }

  /**
   * @param eventIds ids no longer dispatched by this process
   */
  public void untrack(Collection<String> eventIds) {
    inFlight.removeAll(eventIds);
  }

  /**
   * @param eventId an event id
   * @return whether this process is dispatching it right now
   */
  public boolean isInFlight(String eventId) {
    return inFlight.contains(eventId);
  }

  /**
   * @return entries not handed to core yet, excluding the dispatches running in this process
   */
  public List<Map<String, Object>> pendingEntries() {
    return entries(PENDING);
  }

  /**
   * @return entries whose processing started but did not finish, excluding the dispatches running in this process
   */
  public List<Map<String, Object>> interruptedEntries() {
    return entries(DISPATCHED);
  }

  /**
   * The entries in that state that no dispatch of this process owns. A dispatch of this process adds its ids to the
   * in-flight set before it writes its entries and leaves the set only after it removed them, so an entry that was read
   * while its dispatch ran and is no longer in flight afterwards is gone from the journal by then: reading the
   * candidates again after the in-flight check drops those (a dispatch that finished between the two steps would
   * otherwise be taken for an undispatched event and processed a second time).
   */
  private List<Map<String, Object>> entries(String state) {
    List<Map<String, Object>> candidates = new ArrayList<>();
    for (Map<String, Object> entry : journal.find(Map.of("state", state)).exec()) {
      if (!isInFlight(String.valueOf(entry.get("_id")))) {
        candidates.add(entry);
      }
    }
    if (candidates.isEmpty()) {
      return candidates;
    }
    Map<String, Object> stillThere = new LinkedHashMap<>();
    stillThere.put("_id", Map.of("$in", candidates.stream().map(entry -> entry.get("_id")).toList()));
    stillThere.put("state", state);
    Set<Object> ids = new HashSet<>();
    for (Map<String, Object> entry : journal.find(stillThere).exec()) {
      ids.add(entry.get("_id"));
    }
    candidates.removeIf(entry -> !ids.contains(entry.get("_id")));
    return candidates;
  }

  /**
   * @return the number of entries
   */
  public int size() {
    return journal.count(null);
  }
}
