package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.store.Cursor;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.DatastoreOptions;
import com.boostercloud.framework.provider.local.store.IndexOptions;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.types.BoosterJson;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A plain document store used twice by the provider: for websocket connection data
 * ({@code .booster/connections.json}, documents {@link ConnectionData}) and for GraphQL subscriptions
 * ({@code .booster/subscriptions.json}, documents {@code SubscriptionEnvelope}). Port of
 * {@code services/web-socket-registry.ts}.
 *
 * <p>Like TypeScript it declares a TTL index on {@code expirationTime} (2 days), which never expires anything because
 * Booster stores epoch seconds (numbers) there and NeDB only expires dates; rows live until explicitly deleted.
 */
public class WebSocketRegistry {

  /** {@code 2 * 24 * 60 * 60}: the TTL of the {@code expirationTime} index. */
  public static final int EXPIRATION_TTL_SECONDS = 2 * 24 * 60 * 60;

  private final Datastore datastore;
  private volatile boolean indexesAdded;

  /**
   * {@code new WebSocketRegistry(database)}: a registry on a file relative to the working directory.
   *
   * @param database {@link Paths#CONNECTIONS_DATABASE} or {@link Paths#SUBSCRIPTION_DATABASE}
   */
  public WebSocketRegistry(String database) {
    this(Paths.resolve(null, database));
  }

  /**
   * @param file the data file
   */
  public WebSocketRegistry(Path file) {
    this(new Datastore(DatastoreOptions.of(file)));
  }

  /**
   * @param datastore the collection to use
   */
  public WebSocketRegistry(Datastore datastore) {
    this.datastore = Objects.requireNonNull(datastore, "datastore");
    this.datastore.ensureLookupIndex("connectionID");
    this.datastore.ensureLookupIndex("className");
  }

  /**
   * @return the underlying collection ({@code registry.datastore})
   */
  public Datastore datastore() {
    return datastore;
  }

  /**
   * @return whether the data file has been loaded
   */
  public boolean isLoaded() {
    return datastore.isLoaded() && indexesAdded;
  }

  /** {@code loadDatabaseIfNeeded()}: loads the file and adds the TTL index. */
  public void loadDatabaseIfNeeded() {
    if (!indexesAdded || !datastore.isLoaded()) {
      synchronized (this) {
        if (!indexesAdded || !datastore.isLoaded()) {
          datastore.loadDatabaseIfNeeded();
          addIndexes();
          indexesAdded = true;
        }
      }
    }
  }

  /**
   * {@code addIndexes()}: the TTL index on {@code expirationTime}. TypeScript does not await it (a failure would be
   * an unhandled rejection); here a failure is reported to the store's warnings and does not fail the operation.
   */
  public void addIndexes() {
    try {
      datastore.ensureIndex(IndexOptions.ttl("expirationTime", EXPIRATION_TTL_SECONDS));
    } catch (RuntimeException e) {
      System.err.println("[Booster local provider] Could not create the expirationTime index of "
          + datastore.filename() + ": " + e.getMessage());
    }
  }

  /**
   * {@code getCursor(query, createdAt = 1, projections?)}.
   *
   * @param query the query
   * @param createdAt sort direction of {@code createdAt} (stored documents have none, so results keep insertion order)
   * @param projections the projection, or {@code null}
   * @return the cursor
   */
  public Cursor getCursor(Map<String, Object> query, Number createdAt, Map<String, Object> projections) {
    Map<String, Object> sort = new LinkedHashMap<>();
    sort.put("createdAt", createdAt == null ? 1 : createdAt);
    return datastore.find(query, projections).sort(sort);
  }

  /**
   * {@code query(query)}.
   *
   * @param query the query
   * @return the matching documents
   */
  public List<Map<String, Object>> query(Map<String, Object> query) {
    return query(query, 1, null, null);
  }

  /**
   * {@code query(query, createdAt = 1, limit?, projections?)}.
   *
   * @param query the query
   * @param createdAt sort direction of {@code createdAt}
   * @param limit maximum number of results (falsy: no limit)
   * @param projections the projection, or {@code null}
   * @return the matching documents
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
   * {@code store(envelope)}: inserts a document (no uniqueness: storing twice creates two documents).
   *
   * @param envelope a {@link ConnectionData}, a {@code SubscriptionEnvelope} or a raw map (functions/classes are
   *     dropped, as NeDB drops them)
   */
  @SuppressWarnings("unchecked")
  public void store(Object envelope) {
    loadDatabaseIfNeeded();
    datastore.insert(envelope instanceof Map<?, ?> document
        ? (Map<String, Object>) document
        : BoosterJson.toRawMap(envelope));
  }

  /**
   * {@code delete(query)}: removes every matching document.
   *
   * @param query the query
   * @return the number of removed documents
   */
  public int delete(Map<String, Object> query) {
    loadDatabaseIfNeeded();
    return datastore.remove(query, true);
  }

  /**
   * {@code deleteAll()}.
   *
   * @return the number of removed documents
   */
  public int deleteAll() {
    loadDatabaseIfNeeded();
    return datastore.remove(Map.of(), true);
  }

  /**
   * {@code count(query?)}.
   *
   * @param query the query, or {@code null} for all
   * @return the number of matching documents
   */
  public int count(Map<String, Object> query) {
    loadDatabaseIfNeeded();
    return datastore.count(query);
  }

  /**
   * @return the number of documents
   */
  public int count() {
    return count(null);
  }
}
