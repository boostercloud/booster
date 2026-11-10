package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.store.Cursor;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.DatastoreOptions;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.provider.local.store.UpdateOptions;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The event store of the local provider: events <b>and</b> entity snapshots in one collection
 * ({@code .booster/events.json}), told apart by their {@code kind} ({@code "event"} / {@code "snapshot"}). Port of
 * {@code services/event-registry.ts}.
 *
 * <p>Queries are NeDB queries over the stored documents ({@code Map<String, Object>} with the envelope fields plus
 * {@code _id}). Results are sorted by {@code createdAt} (ISO strings, so lexical order is chronological); documents
 * with the same {@code createdAt} come in insertion order (TypeScript: random {@code _id} order).
 */
public class EventRegistry {

  private final Datastore events;

  /** A registry on {@code .booster/events.json} in the working directory ({@code new EventRegistry()}). */
  public EventRegistry() {
    this(Paths.resolve(null, Paths.EVENTS_DATABASE));
  }

  /**
   * @param file the data file
   */
  public EventRegistry(Path file) {
    this(new Datastore(DatastoreOptions.of(file)));
  }

  /**
   * @param datastore the collection to use
   */
  public EventRegistry(Datastore datastore) {
    this.events = Objects.requireNonNull(datastore, "datastore");
    this.events.ensureLookupIndex("entityID");
    this.events.ensureLookupIndex("entityTypeName");
    this.events.ensureLookupIndex("typeName");
  }

  /**
   * @return the underlying collection ({@code eventRegistry.events})
   */
  public Datastore events() {
    return events;
  }

  /**
   * @return whether the data file has been loaded ({@code isLoaded})
   */
  public boolean isLoaded() {
    return events.isLoaded();
  }

  /** {@code loadDatabaseIfNeeded()}. */
  public void loadDatabaseIfNeeded() {
    events.loadDatabaseIfNeeded();
  }

  /**
   * {@code getCursor(query, createdAt = 1, projections?)}.
   *
   * @param query the query
   * @param createdAt sort direction of {@code createdAt}: {@code 1} oldest first, {@code -1} newest first
   * @param projections the projection, or {@code null}
   * @return the cursor
   */
  public Cursor getCursor(Map<String, Object> query, Number createdAt, Map<String, Object> projections) {
    Map<String, Object> sort = new LinkedHashMap<>();
    sort.put("createdAt", createdAt == null ? 1 : createdAt);
    return events.find(query, projections).sort(sort);
  }

  /**
   * {@code query(query)}: matching entries, oldest first.
   *
   * @param query the query
   * @return the stored documents
   */
  public List<Map<String, Object>> query(Map<String, Object> query) {
    return query(query, 1, null, null);
  }

  /**
   * {@code query(query, createdAt = 1, limit?, projections?)}.
   *
   * @param query the query
   * @param createdAt sort direction of {@code createdAt}
   * @param limit maximum number of results; a falsy value ({@code null}, {@code 0}) means no limit
   * @param projections the projection, or {@code null}
   * @return the stored documents
   */
  public List<Map<String, Object>> query(Map<String, Object> query, Number createdAt, Number limit,
      Map<String, Object> projections) {
    loadDatabaseIfNeeded();
    Cursor cursor = getCursor(query, createdAt, projections);
    if (Js.truthy(limit)) {
      cursor = cursor.limit(Js.toNumber(limit));
    }
    return cursor.exec();
  }

  /**
   * {@code replaceOrDeleteItem(id, newValue)}: replaces the document with that {@code _id} by {@code newValue}.
   *
   * @param id the {@code _id}
   * @param newValue the replacement
   */
  public void replaceOrDeleteItem(String id, Map<String, Object> newValue) {
    if (newValue != null) {
      events.update(Map.of("_id", id), newValue, UpdateOptions.MULTI);
    } else {
      events.remove(Map.of("_id", id), true);
    }
  }

  /**
   * {@code replaceOrDeleteItem(id)}: removes the document with that {@code _id}.
   *
   * @param id the {@code _id}
   */
  public void replaceOrDeleteItem(String id) {
    replaceOrDeleteItem(id, null);
  }

  /**
   * {@code queryLatestSnapshot(query)}: the matching snapshot with the greatest {@code snapshottedEventCreatedAt}.
   *
   * @param query the query (the {@code kind: 'snapshot'} condition is added)
   * @return the stored snapshot document, or {@code null}
   */
  public Map<String, Object> queryLatestSnapshot(Map<String, Object> query) {
    loadDatabaseIfNeeded();
    Map<String, Object> snapshotQuery = new LinkedHashMap<>(query);
    snapshotQuery.put("kind", "snapshot");
    List<Map<String, Object>> results =
        events.find(snapshotQuery).sort(Map.of("snapshottedEventCreatedAt", -1)).exec();
    return results.isEmpty() ? null : results.get(0);
  }

  /**
   * {@code store(storableObject)}: inserts an event or snapshot document.
   *
   * @param storableObject the document (an {@code _id} may be given; otherwise one is generated)
   */
  public void store(Map<String, Object> storableObject) {
    loadDatabaseIfNeeded();
    events.insert(storableObject);
  }

  /**
   * Java-only: a fresh {@code _id}, so that callers can reference a document before inserting it.
   *
   * @return the id
   */
  public String createNewId() {
    loadDatabaseIfNeeded();
    return events.createNewId();
  }

  /**
   * {@code deleteAll()}.
   *
   * @return the number of removed documents
   */
  public int deleteAll() {
    loadDatabaseIfNeeded();
    return events.remove(Map.of(), true);
  }

  /**
   * {@code count(query?)}.
   *
   * @param query the query, or {@code null} for all documents
   * @return the number of matching documents
   */
  public int count(Map<String, Object> query) {
    loadDatabaseIfNeeded();
    return events.count(query);
  }

  /**
   * @return the number of documents
   */
  public int count() {
    return count(null);
  }
}
