package com.boostercloud.framework.provider.local.store;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An embedded document collection persisted in the NeDB format: the Java replacement of {@code @seald-io/nedb}'s
 * {@code Datastore} used by the TypeScript local provider. Files written by either implementation can be read by the
 * other.
 *
 * <h2>Storage format</h2>
 * One JSON document per line ({@link Serialization}). Every insert and update appends the full new version of the
 * document; a removal appends {@code {"$$deleted":true,"_id":"…"}}; index definitions are lines
 * {@code {"$$indexCreated":{…}}} (and {@code {"$$indexRemoved":"field"}}). On load the file is replayed (the last line
 * of each {@code _id} wins) and rewritten compacted, crash-safely ({@code <file>~} then an atomic rename).
 *
 * <h2>Durability and recovery</h2>
 * A write method returns only after its lines were written and flushed with {@code FileChannel.force}; if the write
 * fails, the in-memory state is rolled back and the error is thrown. On load an incomplete last line (a write
 * interrupted by a crash) is discarded; other corrupt lines are skipped, and loading fails with
 * {@link CorruptDataFileException} when they exceed {@code corruptAlertThreshold} (NeDB's rule). See
 * {@link Persistence}.
 *
 * <h2>Semantics</h2>
 * Queries, sorting, projections and updates follow NeDB exactly ({@link Model}, {@link Cursor}). Unsorted results and
 * sort ties come in candidate order, which is ascending {@code _id} order unless a persistent index supplied the
 * candidates (NeDB's rule); the ids this store generates grow monotonically ({@link IdGenerator}), so that is insertion
 * order. Unique indexes reject duplicates with {@link NedbError#uniqueViolated}. TTL indexes remove documents whose
 * indexed field holds a {@link NedbDate} older than {@code expireAfterSeconds} (numbers never expire, as in NeDB).
 *
 * <h2>Concurrency</h2>
 * Thread-safe: reads share a read lock, writes (and reads that may expire documents) hold the write lock, so there is
 * a single writer per collection and an update's compare-and-swap is atomic. The lock is fair and works with virtual
 * threads. The datastore loads itself lazily on the first operation; {@link #loadDatabase()} reloads it from disk.
 *
 * <h2>Differences from NeDB</h2>
 * Callbacks/promises become blocking calls; appends are flushed; a torn last line is not counted as corruption; lines
 * are split on {@code \n} only; U+0085, U+2028, U+2029 and surrogates are written escaped; ids are monotonic; failed
 * loads can be retried; memory-only lookup indexes ({@link #ensureLookupIndex(String)}) speed up equality queries
 * without changing results; growth-triggered compaction is available
 * ({@link DatastoreOptions#autocompactionLineThreshold()}).
 */
public class Datastore implements AutoCloseable {

  private final DatastoreOptions options;
  private final Persistence persistence;
  private final IdGenerator idGenerator;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
  private final TreeMap<Object, Map<String, Object>> documents = new TreeMap<>(Model::compareThings);
  private final Map<String, Index> indexes = new LinkedHashMap<>();
  private final Map<String, Integer> ttlIndexes = new LinkedHashMap<>();
  private final Map<String, Index> lookupIndexes = new LinkedHashMap<>();
  private volatile boolean loaded;
  private volatile boolean closed;
  private long obsoleteLines;
  private ScheduledExecutorService autocompaction;

  /**
   * @param options the options
   */
  public Datastore(DatastoreOptions options) {
    this.options = options;
    this.persistence = options.inMemoryOnly()
        ? null
        : new Persistence(options.filename(), options.corruptAlertThreshold(), options.warnings());
    this.idGenerator = new IdGenerator(options.clock());
    this.indexes.put("_id", new Index("_id", true, false, false));
  }

  /**
   * @param filename the data file
   */
  public Datastore(Path filename) {
    this(DatastoreOptions.of(filename));
  }

  /**
   * @return the data file, or {@code null} for an in-memory datastore
   */
  public Path filename() {
    return options.filename();
  }

  /**
   * @return whether the data file has been loaded
   */
  public boolean isLoaded() {
    return loaded;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Loading and compaction
  // ---------------------------------------------------------------------------------------------------------------

  /** Loads the data file unless it is already loaded (operations call this implicitly). */
  public void loadDatabaseIfNeeded() {
    if (!loaded) {
      Lock write = lock.writeLock();
      write.lock();
      try {
        if (!loaded) {
          loadLocked();
        }
      } finally {
        write.unlock();
      }
    }
  }

  /**
   * {@code loadDatabaseAsync()}: (re)loads the data file, recreating the persisted indexes, and rewrites it compacted.
   *
   * @throws CorruptDataFileException when too many lines are corrupt
   * @throws NedbError when the data violates a unique index or the file cannot be read or written
   */
  public void loadDatabase() {
    Lock write = lock.writeLock();
    write.lock();
    try {
      loadLocked();
    } finally {
      write.unlock();
    }
  }

  private void loadLocked() {
    ensureOpen();
    loaded = false;
    resetIndexes(List.of());
    documents.clear();
    if (persistence == null) {
      loaded = true;
      return;
    }
    try {
      persistence.closeAppendChannel();
      persistence.ensureParentDirectoryExists();
      persistence.ensureDatafileIntegrity();
      Persistence.TreatedData treated = persistence.read();
      for (Map.Entry<String, Map<String, Object>> definition : treated.indexes().entrySet()) {
        indexes.put(definition.getKey(), new Index(IndexOptions.fromDocument(definition.getValue())));
      }
      try {
        resetIndexes(treated.data());
      } catch (NedbError e) {
        resetIndexes(List.of());
        throw e;
      }
      for (Map<String, Object> document : treated.data()) {
        documents.put(document.get("_id"), document);
      }
      persistence.resetFailure();
      persistCachedDatabase();
      loaded = true;
    } catch (IOException e) {
      throw new NedbError("Could not load the data file " + options.filename() + ": " + e.getMessage(), e);
    } finally {
      if (!loaded) {
        documents.clear();
        resetIndexes(List.of());
      }
    }
  }

  private void resetIndexes(List<Map<String, Object>> data) {
    for (Index index : indexes.values()) {
      index.reset(data);
    }
    for (Index index : lookupIndexes.values()) {
      index.reset(data);
    }
  }

  /** {@code persistCachedDatabase}: rewrites the data file with the live documents and index definitions. */
  private void persistCachedDatabase() throws IOException {
    if (persistence == null) {
      return;
    }
    List<String> lines = new ArrayList<>(documents.size() + indexes.size());
    for (Map<String, Object> document : documents.values()) {
      lines.add(Serialization.serialize(document));
    }
    for (Index index : indexes.values()) {
      if (!index.fieldName().equals("_id")) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("fieldName", index.fieldName());
        definition.put("unique", index.unique());
        definition.put("sparse", index.sparse());
        lines.add(Serialization.serialize(Map.of("$$indexCreated", definition)));
      }
    }
    persistence.crashSafeWrite(lines);
    obsoleteLines = 0;
  }

  /** {@code compactDatafileAsync()}: rewrites the data file without obsolete lines. */
  public void compactDatafile() {
    loadDatabaseIfNeeded();
    Lock write = lock.writeLock();
    write.lock();
    try {
      ensureOpen();
      persistCachedDatabase();
    } catch (IOException e) {
      throw new NedbError("Could not compact the data file " + options.filename() + ": " + e.getMessage(), e);
    } finally {
      write.unlock();
    }
  }

  /**
   * {@code setAutocompactionInterval(interval)}: compacts periodically (at least every 5 seconds, NeDB's minimum).
   *
   * @param interval the interval
   */
  public synchronized void setAutocompactionInterval(Duration interval) {
    stopAutocompaction();
    long millis = Math.max(interval.toMillis(), 5000);
    autocompaction = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("booster-store-autocompaction").factory());
    autocompaction.scheduleWithFixedDelay(() -> {
      try {
        compactDatafile();
      } catch (RuntimeException e) {
        options.warnings().accept("Autocompaction of " + options.filename() + " failed: " + e.getMessage());
      }
    }, millis, millis, TimeUnit.MILLISECONDS);
  }

  /** {@code stopAutocompaction()}. */
  public synchronized void stopAutocompaction() {
    if (autocompaction != null) {
      autocompaction.shutdownNow();
      autocompaction = null;
    }
  }

  /** Stops autocompaction and releases the data file; later operations fail. */
  @Override
  public void close() {
    stopAutocompaction();
    Lock write = lock.writeLock();
    write.lock();
    try {
      closed = true;
      if (persistence != null) {
        persistence.closeAppendChannel();
      }
    } finally {
      write.unlock();
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new NedbError("The datastore " + (options.filename() == null ? "(in memory)" : options.filename())
          + " is closed");
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Indexes
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code ensureIndexAsync(options)}: creates a persistent index unless one exists for the field (an index restored
   * from the data file counts, even without its TTL, as in NeDB).
   *
   * @param indexOptions the index
   * @throws NedbError {@code Cannot create an index without a fieldName}, {@code Cannot use comma in index fieldName},
   *     or a unique violation of the existing data
   */
  public void ensureIndex(IndexOptions indexOptions) {
    if (indexOptions.fieldName() == null || indexOptions.fieldName().isEmpty()) {
      throw new NedbError("Cannot create an index without a fieldName");
    }
    List<String> fields = new ArrayList<>(List.of(indexOptions.fieldName().split(",", -1)));
    if (fields.size() > 1) {
      throw new NedbError("Cannot use comma in index fieldName");
    }
    loadDatabaseIfNeeded();
    Lock write = lock.writeLock();
    write.lock();
    try {
      ensureOpen();
      String fieldName = indexOptions.fieldName();
      if (indexes.containsKey(fieldName)) {
        return;
      }
      Index index = new Index(indexOptions);
      index.insert(new ArrayList<>(documents.values()));
      indexes.put(fieldName, index);
      if (indexOptions.expireAfterSeconds() != null) {
        ttlIndexes.put(fieldName, indexOptions.expireAfterSeconds());
      }
      try {
        persist(List.of(Serialization.serialize(Map.of("$$indexCreated", indexOptions.toDocument()))), 0);
      } catch (RuntimeException e) {
        indexes.remove(fieldName);
        ttlIndexes.remove(fieldName);
        throw e;
      }
    } finally {
      write.unlock();
    }
  }

  /**
   * {@code removeIndexAsync(fieldName)}: removes a persistent index.
   *
   * @param fieldName the indexed field
   */
  public void removeIndex(String fieldName) {
    loadDatabaseIfNeeded();
    Lock write = lock.writeLock();
    write.lock();
    try {
      ensureOpen();
      if (fieldName.equals("_id")) {
        return;
      }
      indexes.remove(fieldName);
      ttlIndexes.remove(fieldName);
      persist(List.of(Serialization.serialize(Map.of("$$indexRemoved", fieldName))), 2);
    } finally {
      write.unlock();
    }
  }

  /**
   * Adds a memory-only lookup index on a field. It is never persisted and only narrows the candidates of queries with
   * a top-level equality on that field when no persistent index applies, so results (and their order) are exactly
   * what a full scan gives.
   *
   * @param fieldName the field (dot path)
   */
  public void ensureLookupIndex(String fieldName) {
    Lock write = lock.writeLock();
    write.lock();
    try {
      if (lookupIndexes.containsKey(fieldName)) {
        return;
      }
      Index index = new Index(fieldName, false, true, true);
      index.insert(new ArrayList<>(documents.values()));
      lookupIndexes.put(fieldName, index);
    } finally {
      write.unlock();
    }
  }

  /**
   * @return the names of the persistent indexes (including {@code _id})
   */
  public List<String> indexNames() {
    Lock read = lock.readLock();
    read.lock();
    try {
      return new ArrayList<>(indexes.keySet());
    } finally {
      read.unlock();
    }
  }

  private void addToIndexes(Map<String, Object> document) {
    List<Index> done = new ArrayList<>();
    try {
      for (Index index : indexes.values()) {
        index.insert(document);
        done.add(index);
      }
    } catch (NedbError e) {
      for (Index index : done) {
        index.remove(document);
      }
      throw e;
    }
    for (Index index : lookupIndexes.values()) {
      index.insert(document);
    }
  }

  private void removeFromIndexes(Map<String, Object> document) {
    for (Index index : indexes.values()) {
      index.remove(document);
    }
    for (Index index : lookupIndexes.values()) {
      index.remove(document);
    }
  }

  private void updateIndexes(List<Index.Modification> modifications) {
    List<Index> done = new ArrayList<>();
    try {
      for (Index index : indexes.values()) {
        index.update(modifications);
        done.add(index);
      }
    } catch (NedbError e) {
      for (Index index : done) {
        index.revertUpdate(modifications);
      }
      throw e;
    }
    for (Index index : lookupIndexes.values()) {
      index.update(modifications);
    }
  }

  private void revertIndexes(List<Index.Modification> modifications) {
    for (Index index : indexes.values()) {
      index.revertUpdate(modifications);
    }
    for (Index index : lookupIndexes.values()) {
      index.revertUpdate(modifications);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Candidates
  // ---------------------------------------------------------------------------------------------------------------

  private static boolean isBasicValue(Object value) {
    return value == null || value instanceof String || value instanceof Number || value instanceof Boolean
        || value instanceof NedbDate;
  }

  private static boolean hasAny(Object value, String... keys) {
    if (!Js.truthy(value) || !(value instanceof Map<?, ?> map)) {
      return false;
    }
    for (String key : keys) {
      if (map.containsKey(key)) {
        return true;
      }
    }
    return false;
  }

  /** {@code _getRawCandidates(query)}. */
  private List<Map<String, Object>> rawCandidates(Map<String, Object> query) {
    Map.Entry<String, Object> usable = null;
    for (Map.Entry<String, Object> entry : query.entrySet()) {
      if (isBasicValue(entry.getValue()) && indexes.containsKey(entry.getKey())) {
        usable = entry;
      }
    }
    if (usable != null) {
      return toDocuments(indexes.get(usable.getKey()).getMatching(usable.getValue()));
    }
    for (Map.Entry<String, Object> entry : query.entrySet()) {
      if (hasAny(entry.getValue(), "$in") && indexes.containsKey(entry.getKey())) {
        usable = entry;
      }
    }
    if (usable != null) {
      return toDocuments(indexes.get(usable.getKey()).getMatching(((Map<?, ?>) usable.getValue()).get("$in")));
    }
    for (Map.Entry<String, Object> entry : query.entrySet()) {
      if (hasAny(entry.getValue(), "$lt", "$lte", "$gt", "$gte") && indexes.containsKey(entry.getKey())) {
        usable = entry;
      }
    }
    if (usable != null) {
      return toDocuments(indexes.get(usable.getKey()).getBetweenBounds((Map<?, ?>) usable.getValue()));
    }
    List<Object> best = null;
    for (Map.Entry<String, Object> entry : query.entrySet()) {
      Index lookup = lookupIndexes.get(entry.getKey());
      if (lookup != null && isBasicValue(entry.getValue())) {
        List<Object> ids = lookup.getMatching(entry.getValue());
        if (best == null || ids.size() < best.size()) {
          best = ids;
        }
      }
    }
    if (best != null) {
      return toDocuments(best);
    }
    return new ArrayList<>(documents.values());
  }

  private List<Map<String, Object>> toDocuments(Collection<Object> ids) {
    List<Map<String, Object>> result = new ArrayList<>(ids.size());
    for (Object id : ids) {
      Map<String, Object> document = documents.get(id);
      if (document != null) {
        result.add(document);
      }
    }
    return result;
  }

  /** {@code _getCandidatesAsync(query, dontExpireStaleDocs)}; the caller holds the write lock when TTLs exist. */
  private List<Map<String, Object>> candidates(Map<String, Object> query, boolean dontExpireStaleDocs) {
    List<Map<String, Object>> docs = rawCandidates(query);
    if (dontExpireStaleDocs || ttlIndexes.isEmpty()) {
      return docs;
    }
    long now = options.clock().getAsLong();
    List<Map<String, Object>> valid = new ArrayList<>(docs.size());
    List<Map<String, Object>> expired = new ArrayList<>();
    for (Map<String, Object> document : docs) {
      boolean isExpired = false;
      for (Map.Entry<String, Integer> ttl : ttlIndexes.entrySet()) {
        Object value = Js.get(document, ttl.getKey());
        if (value instanceof NedbDate date && now > date.epochMillis() + ttl.getValue() * 1000.0) {
          isExpired = true;
        }
      }
      if (isExpired) {
        expired.add(document);
      } else {
        valid.add(document);
      }
    }
    if (!expired.isEmpty()) {
      removeDocuments(expired);
    }
    return valid;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Operations
  // ---------------------------------------------------------------------------------------------------------------

  private Lock readLockFor() {
    return ttlIndexes.isEmpty() ? lock.readLock() : lock.writeLock();
  }

  /**
   * {@code find(query)}.
   *
   * @param query the query
   * @return a cursor
   */
  public Cursor find(Map<String, Object> query) {
    return new Cursor(this, query, null);
  }

  /**
   * {@code find(query, projection)}.
   *
   * @param query the query
   * @param projection the projection, or {@code null}
   * @return a cursor
   */
  public Cursor find(Map<String, Object> query, Map<String, Object> projection) {
    return new Cursor(this, query, projection);
  }

  List<Map<String, Object>> execute(Cursor cursor) {
    loadDatabaseIfNeeded();
    Lock held = readLockFor();
    held.lock();
    try {
      ensureOpen();
      return cursor.execute(candidates(cursor.query(), false));
    } finally {
      held.unlock();
    }
  }

  /**
   * {@code countAsync(query)}.
   *
   * @param query the query ({@code null} counts every document)
   * @return the number of matching documents
   */
  public int count(Map<String, Object> query) {
    loadDatabaseIfNeeded();
    Map<String, Object> effective = query == null ? Map.of() : query;
    Lock held = readLockFor();
    held.lock();
    try {
      ensureOpen();
      int count = 0;
      for (Map<String, Object> candidate : candidates(effective, false)) {
        if (Model.match(candidate, effective)) {
          count++;
        }
      }
      return count;
    } finally {
      held.unlock();
    }
  }

  /**
   * @return copies of every document, in {@code _id} order ({@code getAllData()})
   */
  public List<Map<String, Object>> getAllData() {
    loadDatabaseIfNeeded();
    Lock read = lock.readLock();
    read.lock();
    try {
      List<Map<String, Object>> copies = new ArrayList<>(documents.size());
      for (Map<String, Object> document : documents.values()) {
        copies.add(Model.copyDocument(document));
      }
      return copies;
    } finally {
      read.unlock();
    }
  }

  /**
   * @return a new {@code _id} that no document uses ({@code _createNewId()})
   */
  public String createNewId() {
    Lock read = lock.readLock();
    read.lock();
    try {
      String id = idGenerator.next();
      while (documents.containsKey(id)) {
        id = idGenerator.next();
      }
      return id;
    } finally {
      read.unlock();
    }
  }

  private Map<String, Object> prepareForInsertion(Map<String, Object> document) {
    Map<String, Object> prepared = Model.copyDocument(document);
    if (!prepared.containsKey("_id")) {
      String id = idGenerator.next();
      while (documents.containsKey(id)) {
        id = idGenerator.next();
      }
      prepared.put("_id", id);
    }
    Model.checkObject(prepared);
    return prepared;
  }

  /**
   * {@code insertAsync(document)}: stores a copy of the document, generating its {@code _id} when absent. The caller's
   * map is not modified.
   *
   * @param document the document
   * @return a copy of the stored document (with {@code _id})
   * @throws NedbError on unique violations, invalid field names or write failures (nothing is stored then)
   */
  public Map<String, Object> insert(Map<String, Object> document) {
    return insert(List.of(document)).get(0);
  }

  /**
   * {@code insertAsync([documents])}: stores several documents atomically in memory and with a single flushed append.
   *
   * @param newDocuments the documents
   * @return copies of the stored documents
   */
  public List<Map<String, Object>> insert(List<? extends Map<String, Object>> newDocuments) {
    loadDatabaseIfNeeded();
    Lock write = lock.writeLock();
    write.lock();
    try {
      ensureOpen();
      List<Map<String, Object>> prepared = new ArrayList<>(newDocuments.size());
      for (Map<String, Object> document : newDocuments) {
        prepared.add(prepareForInsertion(document));
      }
      List<Map<String, Object>> inserted = new ArrayList<>();
      try {
        for (Map<String, Object> document : prepared) {
          addToIndexes(document);
          documents.put(document.get("_id"), document);
          inserted.add(document);
        }
      } catch (NedbError e) {
        for (Map<String, Object> document : inserted) {
          removeFromIndexes(document);
          documents.remove(document.get("_id"));
        }
        throw e;
      }
      List<String> lines = new ArrayList<>(prepared.size());
      for (Map<String, Object> document : prepared) {
        lines.add(Serialization.serialize(document));
      }
      try {
        persist(lines, 0);
      } catch (RuntimeException e) {
        for (Map<String, Object> document : prepared) {
          removeFromIndexes(document);
          documents.remove(document.get("_id"));
        }
        throw e;
      }
      List<Map<String, Object>> copies = new ArrayList<>(prepared.size());
      for (Map<String, Object> document : prepared) {
        copies.add(Model.copyDocument(document));
      }
      return copies;
    } finally {
      write.unlock();
    }
  }

  /**
   * {@code updateAsync(query, update, options)}: replaces the matching document(s) with {@code update}, or applies its
   * modifiers ({@code $set}, ...). Without {@code multi} only the first match is updated. With {@code upsert}, a
   * document is inserted when nothing matches.
   *
   * @param query the query
   * @param update the replacement document or the modifiers
   * @param updateOptions the options
   * @return the number of affected documents (and copies of them with {@code returnUpdatedDocs})
   * @throws NedbError on unique violations, invalid updates or write failures (nothing changes then)
   */
  public UpdateResult update(Map<String, Object> query, Map<String, Object> update, UpdateOptions updateOptions) {
    loadDatabaseIfNeeded();
    Map<String, Object> effectiveQuery = query == null ? Map.of() : query;
    Lock write = lock.writeLock();
    write.lock();
    try {
      ensureOpen();
      if (updateOptions.upsert()) {
        List<Map<String, Object>> existing = new Cursor(this, effectiveQuery, null).limit(1)
            .execute(candidates(effectiveQuery, false));
        if (existing.size() != 1) {
          Map<String, Object> toBeInserted;
          try {
            Model.checkObject(update);
            toBeInserted = update;
          } catch (NedbError e) {
            @SuppressWarnings("unchecked")
            Map<String, Object> base = (Map<String, Object>) Model.deepCopy(effectiveQuery, true);
            toBeInserted = Model.modify(base, update);
          }
          // the write lock is reentrant: the insertion happens atomically with the lookup above
          return new UpdateResult(1, insert(toBeInserted), true);
        }
      }
      int numReplaced = 0;
      List<Index.Modification> modifications = new ArrayList<>();
      for (Map<String, Object> candidate : candidates(effectiveQuery, false)) {
        if (Model.match(candidate, effectiveQuery) && (updateOptions.multi() || numReplaced == 0)) {
          numReplaced++;
          modifications.add(new Index.Modification(candidate, Model.modify(candidate, update)));
        }
      }
      updateIndexes(modifications);
      for (Index.Modification modification : modifications) {
        documents.put(modification.newDoc().get("_id"), modification.newDoc());
      }
      List<String> lines = new ArrayList<>(modifications.size());
      for (Index.Modification modification : modifications) {
        lines.add(Serialization.serialize(modification.newDoc()));
      }
      try {
        persist(lines, modifications.size());
      } catch (RuntimeException e) {
        revertIndexes(modifications);
        for (Index.Modification modification : modifications) {
          documents.put(modification.oldDoc().get("_id"), modification.oldDoc());
        }
        throw e;
      }
      if (!updateOptions.returnUpdatedDocs()) {
        return new UpdateResult(numReplaced, null, false);
      }
      List<Map<String, Object>> updated = new ArrayList<>();
      for (Index.Modification modification : modifications) {
        updated.add(Model.copyDocument(modification.newDoc()));
      }
      Object affected = updateOptions.multi() ? updated : (updated.isEmpty() ? null : updated.get(0));
      return new UpdateResult(numReplaced, affected, false);
    } finally {
      write.unlock();
    }
  }

  /**
   * {@code removeAsync(query, { multi })}.
   *
   * @param query the query
   * @param multi remove every match instead of the first one
   * @return the number of removed documents
   */
  public int remove(Map<String, Object> query, boolean multi) {
    loadDatabaseIfNeeded();
    Map<String, Object> effectiveQuery = query == null ? Map.of() : query;
    Lock write = lock.writeLock();
    write.lock();
    try {
      ensureOpen();
      List<Map<String, Object>> removed = new ArrayList<>();
      for (Map<String, Object> candidate : candidates(effectiveQuery, true)) {
        if (Model.match(candidate, effectiveQuery) && (multi || removed.isEmpty())) {
          removed.add(candidate);
        }
      }
      removeDocuments(removed);
      return removed.size();
    } finally {
      write.unlock();
    }
  }

  private void removeDocuments(List<Map<String, Object>> removed) {
    if (removed.isEmpty()) {
      return;
    }
    List<String> lines = new ArrayList<>(removed.size());
    for (Map<String, Object> document : removed) {
      Map<String, Object> marker = new LinkedHashMap<>();
      marker.put("$$deleted", true);
      marker.put("_id", document.get("_id"));
      lines.add(Serialization.serialize(marker));
    }
    persist(lines, removed.size() * 2L);
    for (Map<String, Object> document : removed) {
      removeFromIndexes(document);
      documents.remove(document.get("_id"));
    }
  }

  /**
   * @return the number of documents
   */
  public int size() {
    loadDatabaseIfNeeded();
    Lock read = lock.readLock();
    read.lock();
    try {
      return documents.size();
    } finally {
      read.unlock();
    }
  }

  /**
   * @return the number of lines of the data file that no longer describe the live state (since the last compaction)
   */
  public long obsoleteLines() {
    Lock read = lock.readLock();
    read.lock();
    try {
      return obsoleteLines;
    } finally {
      read.unlock();
    }
  }

  /** Appends lines (write lock held) and compacts when the configured growth threshold is reached. */
  private void persist(List<String> lines, long newlyObsoleteLines) {
    if (persistence == null || lines.isEmpty()) {
      return;
    }
    persistence.append(lines);
    obsoleteLines += newlyObsoleteLines;
    int threshold = options.autocompactionLineThreshold();
    if (threshold > 0 && obsoleteLines >= threshold && obsoleteLines > documents.size()) {
      try {
        persistCachedDatabase();
      } catch (IOException | RuntimeException e) {
        options.warnings().accept("Automatic compaction of " + options.filename() + " failed: " + e.getMessage());
      }
    }
  }
}
