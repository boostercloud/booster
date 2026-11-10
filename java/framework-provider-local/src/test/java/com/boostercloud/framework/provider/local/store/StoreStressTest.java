package com.boostercloud.framework.provider.local.store;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stress tests of the embedded store: many concurrent virtual-thread writers and readers while the file is compacted,
 * crash simulation by truncating (or zero-filling) the data file at every interesting byte offset, and the cost of
 * loading and querying 100k documents. The out-of-process variant (a writer killed with {@code SIGKILL}) is
 * {@link KillDuringWriteStressTest}.
 */
class StoreStressTest {

  @TempDir
  Path directory;

  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
  private final List<Datastore> opened = Collections.synchronizedList(new ArrayList<>());

  @AfterEach
  void tearDown() {
    opened.forEach(Datastore::close);
  }

  private Datastore open(DatastoreOptions options) {
    Datastore datastore = new Datastore(options.withWarnings(warnings::add));
    opened.add(datastore);
    return datastore;
  }

  static Map<String, Map<String, Object>> byId(List<Map<String, Object>> documents) {
    Map<String, Map<String, Object>> result = new TreeMap<>();
    for (Map<String, Object> document : documents) {
      result.put((String) document.get("_id"), document);
    }
    return result;
  }

  private List<String> corruptionWarnings() {
    synchronized (warnings) {
      return warnings.stream().filter(w -> !w.startsWith("Discarding an incomplete last line")).toList();
    }
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Concurrency and compaction under load
  // -----------------------------------------------------------------------------------------------------------------

  /** Each writer owns its keys and keeps the model of what it acknowledged; the union is the expected state. */
  @Test
  @SuppressWarnings("unchecked")
  void concurrentWritersReadersAndCompactionsLoseNothing() throws Exception {
    Path file = directory.resolve("load.json");
    Datastore datastore = open(DatastoreOptions.of(file).withAutocompactionLineThreshold(300));
    datastore.ensureIndex(IndexOptions.unique("key", true));
    datastore.ensureLookupIndex("owner");
    int writers = 16;
    int operationsPerWriter = 120;
    Map<String, Map<String, Object>> expected = new ConcurrentHashMap<>();
    AtomicBoolean writing = new AtomicBoolean(true);
    AtomicInteger compactions = new AtomicInteger();
    AtomicInteger reads = new AtomicInteger();
    AtomicReference<Throwable> readerFailure = new AtomicReference<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> writerTasks = new ArrayList<>();
      for (int w = 0; w < writers; w++) {
        int writer = w;
        writerTasks.add(executor.submit(() -> {
          Random random = new Random(writer);
          Map<String, Map<String, Object>> mine = new HashMap<>();
          for (int i = 0; i < operationsPerWriter; i++) {
            int choice = random.nextInt(10);
            if (mine.isEmpty() || choice < 5) {
              Map<String, Object> stored = datastore.insert(map("key", "w" + writer + "-" + i, "owner", writer,
                  "counter", 0, "check", checksum(writer, 0)));
              mine.put((String) stored.get("_id"), stored);
            } else if (choice < 8) {
              String id = pick(random, mine);
              Map<String, Object> current = mine.get(id);
              int counter = ((Number) current.get("counter")).intValue() + 1;
              Map<String, Object> replacement = map("key", current.get("key"), "owner", writer, "counter", counter,
                  "check", checksum(writer, counter));
              UpdateResult result = datastore.update(map("_id", id), replacement,
                  new UpdateOptions(false, false, true));
              assertThat(result.numAffected()).isEqualTo(1);
              mine.put(id, (Map<String, Object>) result.affectedDocuments());
            } else {
              String id = pick(random, mine);
              assertThat(datastore.remove(map("_id", id), false)).isEqualTo(1);
              mine.remove(id);
            }
          }
          expected.putAll(mine);
          return null;
        }));
      }
      Future<?> compactor = executor.submit(() -> {
        while (writing.get()) {
          datastore.compactDatafile();
          compactions.incrementAndGet();
        }
        return null;
      });
      List<Future<?>> readers = new ArrayList<>();
      for (int r = 0; r < 8; r++) {
        int owner = r;
        readers.add(executor.submit(() -> {
          try {
            while (writing.get()) {
              for (Map<String, Object> document : datastore.find(map("owner", owner)).exec()) {
                // every document a reader sees is a complete version written by one operation
                assertThat(document.get("check"))
                    .isEqualTo(checksum(owner, ((Number) document.get("counter")).intValue()));
              }
              datastore.count(map("counter", map("$gte", 0)));
              reads.incrementAndGet();
            }
          } catch (Throwable e) {
            readerFailure.set(e);
          }
          return null;
        }));
      }
      for (Future<?> task : writerTasks) {
        task.get();
      }
      writing.set(false);
      compactor.get();
      for (Future<?> reader : readers) {
        reader.get();
      }
    }
    assertThat(readerFailure.get()).isNull();
    assertThat(compactions.get()).isPositive();
    assertThat(reads.get()).isPositive();
    assertThat(byId(datastore.getAllData())).isEqualTo(new TreeMap<>(expected));
    assertThat(Files.exists(directory.resolve("load.json~"))).isFalse();

    Datastore reopened = open(DatastoreOptions.of(file));
    reopened.loadDatabase();
    assertThat(byId(reopened.getAllData())).isEqualTo(new TreeMap<>(expected));
    assertThat(reopened.indexNames()).containsExactly("_id", "key");
    assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).hasSize(expected.size() + 1);
    assertThat(corruptionWarnings()).isEmpty();
  }

  static String checksum(int owner, int counter) {
    return owner + ":" + counter + ":" + Integer.toHexString((owner * 31 + counter) * 0x9E3779B9);
  }

  static String pick(Random random, Map<String, Map<String, Object>> documents) {
    List<String> ids = new ArrayList<>(documents.keySet());
    Collections.sort(ids);
    return ids.get(random.nextInt(ids.size()));
  }

  @Test
  void uniqueKeysStayUniqueUnderConcurrentInserts() {
    Datastore datastore = open(DatastoreOptions.of(directory.resolve("unique.json")));
    datastore.ensureIndex(IndexOptions.unique("uniqueKey", true));
    int threads = 200;
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int t = 0; t < threads; t++) {
        int thread = t;
        executor.submit(() -> {
          for (int key = 0; key < 20; key++) {
            try {
              datastore.insert(map("uniqueKey", "RM_" + key + "_1", "writer", thread));
              accepted.incrementAndGet();
            } catch (NedbError e) {
              if (!NedbError.UNIQUE_VIOLATED.equals(e.errorType())) {
                failure.set(e);
              }
              rejected.incrementAndGet();
            }
          }
        });
      }
    }
    assertThat(failure.get()).isNull();
    assertThat(accepted.get()).isEqualTo(20);
    assertThat(rejected.get()).isEqualTo(threads * 20 - 20);
    Datastore reopened = open(DatastoreOptions.of(directory.resolve("unique.json")));
    assertThat(reopened.count(map())).isEqualTo(20);
  }

  /** Readers racing with the creation of a TTL index that expires documents must never see a broken collection. */
  @Test
  void creatingATtlIndexWhileReadersRunIsSafe() throws Exception {
    for (int round = 0; round < 5; round++) {
      Datastore datastore = open(DatastoreOptions.of(directory.resolve("ttl-" + round + ".json")));
      List<Map<String, Object>> documents = new ArrayList<>();
      for (int i = 0; i < 3_000; i++) {
        documents.add(map("n", i, "expiresAt", i % 2 == 0 ? java.time.Instant.EPOCH : "never"));
      }
      datastore.insert(documents);
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int r = 0; r < 16; r++) {
          executor.submit(() -> {
            try {
              while (running.get()) {
                int seen = datastore.find(map()).exec().size();
                assertThat(seen).isIn(1_500, 3_000);
                datastore.count(map("n", map("$gte", 0)));
              }
            } catch (Throwable e) {
              failure.compareAndSet(null, e);
            }
          });
        }
        Thread.sleep(20);
        datastore.ensureIndex(IndexOptions.ttl("expiresAt", 60));
        Thread.sleep(20);
        running.set(false);
      }
      assertThat(failure.get()).isNull();
      assertThat(datastore.count(map())).isEqualTo(1_500);
    }
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Crash simulation: truncated and zero-filled files
  // -----------------------------------------------------------------------------------------------------------------

  /** A data file built by single-line operations, with the expected state after each line. */
  private record History(byte[] bytes, List<Integer> lineEnds, List<Map<String, Map<String, Object>>> states) {

    /** The number of lines whose JSON text is complete within the first {@code length} bytes. */
    int completeLines(int length, boolean newlineRequired) {
      int count = 0;
      for (int end : lineEnds) {
        if (newlineRequired ? end < length : end <= length) {
          count++;
        }
      }
      return count;
    }
  }

  @SuppressWarnings("unchecked")
  private History history(int operations) throws IOException {
    Path file = directory.resolve("history.json");
    Datastore writer = open(DatastoreOptions.of(file));
    writer.loadDatabase();
    List<Map<String, Map<String, Object>>> states = new ArrayList<>();
    Map<String, Map<String, Object>> model = new TreeMap<>();
    states.add(new TreeMap<>());
    // line 1: the index definition; the documents do not change
    writer.ensureIndex(IndexOptions.unique("key", true));
    states.add(new TreeMap<>(model));
    Random random = new Random(7);
    for (int i = 0; i < operations; i++) {
      int choice = random.nextInt(10);
      if (model.isEmpty() || choice < 5) {
        Map<String, Object> stored = writer.insert(document(random, "k" + i, 0));
        model.put((String) stored.get("_id"), stored);
      } else if (choice < 8) {
        String id = pick(random, model);
        Map<String, Object> current = model.get(id);
        Map<String, Object> replacement =
            document(random, (String) current.get("key"), ((Number) current.get("counter")).intValue() + 1);
        UpdateResult result = writer.update(map("_id", id), replacement, new UpdateOptions(false, false, true));
        model.put(id, (Map<String, Object>) result.affectedDocuments());
      } else {
        String id = pick(random, model);
        writer.remove(map("_id", id), false);
        model.remove(id);
      }
      states.add(new TreeMap<>(model));
    }
    writer.close();
    byte[] bytes = Files.readAllBytes(file);
    List<Integer> lineEnds = new ArrayList<>();
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == '\n') {
        lineEnds.add(i);
      }
    }
    // one line per operation, so line boundaries are operation boundaries
    assertThat(lineEnds).hasSize(operations + 1);
    return new History(bytes, lineEnds, states);
  }

  static Map<String, Object> document(Random random, String key, int counter) {
    StringBuilder text = new StringBuilder();
    int length = 1 + random.nextInt(40);
    String alphabet = "aZ9 ñ€é中\"\\";
    for (int i = 0; i < length; i++) {
      // multi-byte UTF-8 characters, so that offsets also fall inside characters
      text.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return map("key", key, "counter", counter, "text", text.toString(),
        "nested", map("list", list(counter, "x" + counter, map("deep", random.nextBoolean())), "nothing", null));
  }

  private Map<String, Map<String, Object>> load(Path file, byte[] content) throws IOException {
    Files.write(file, content);
    Datastore datastore = open(DatastoreOptions.of(file));
    datastore.loadDatabase();
    Map<String, Map<String, Object>> state = byId(datastore.getAllData());
    datastore.close();
    return state;
  }

  @Test
  void truncatingTheFileAtAnyOffsetKeepsEveryCompleteWriteBeforeIt() throws IOException {
    History history = history(100);
    List<Integer> offsets = new ArrayList<>();
    for (int end : history.lineEnds()) {
      offsets.add(end - 1);
      offsets.add(end);
      offsets.add(end + 1);
    }
    Random random = new Random(11);
    for (int i = 0; i < 100; i++) {
      offsets.add(random.nextInt(history.bytes().length + 1));
    }
    offsets.add(0);
    offsets.add(history.bytes().length);
    Path file = directory.resolve("truncated.json");
    int checked = 0;
    for (int offset : offsets) {
      byte[] truncated = Arrays.copyOf(history.bytes(), offset);
      int complete = history.completeLines(offset, false);
      Map<String, Map<String, Object>> state = load(file, truncated);
      assertThat(state).as("state after truncating at byte %d", offset).isEqualTo(history.states().get(complete));
      if (checked++ % 10 == 0) {
        // the torn tail is gone after the load: new writes do not glue onto it
        Datastore datastore = open(DatastoreOptions.of(file));
        assertThat(datastore.indexNames()).hasSize(complete >= 1 ? 2 : 1);
        Map<String, Object> added = datastore.insert(map("key", "after-crash", "counter", 0));
        datastore.close();
        Datastore reopened = open(DatastoreOptions.of(file));
        Map<String, Map<String, Object>> expected = new TreeMap<>(history.states().get(complete));
        expected.put((String) added.get("_id"), added);
        assertThat(byId(reopened.getAllData())).isEqualTo(expected);
        reopened.close();
      }
    }
    assertThat(corruptionWarnings()).isEmpty();
  }

  @Test
  void aZeroFilledTailIsAnInterruptedWriteToo() throws IOException {
    History history = history(80);
    Path file = directory.resolve("zero-filled.json");
    Random random = new Random(13);
    List<Integer> offsets = new ArrayList<>(List.of(0, history.bytes().length));
    for (int end : history.lineEnds()) {
      offsets.add(end);
      offsets.add(end + 1);
    }
    for (int i = 0; i < 60; i++) {
      offsets.add(random.nextInt(history.bytes().length + 1));
    }
    for (int offset : offsets) {
      // file systems that allocate blocks before writing them can leave zeros after a crash
      byte[] content = new byte[offset + 512];
      System.arraycopy(history.bytes(), 0, content, 0, offset);
      int complete = history.completeLines(offset, true);
      assertThat(load(file, content)).as("state with zeros from byte %d", offset)
          .isEqualTo(history.states().get(complete));
    }
    assertThat(corruptionWarnings()).isEmpty();
  }

  @Test
  void aCrashDuringCompactionLosesNothing() throws IOException {
    History history = history(80);
    Path file = directory.resolve("compacting.json");
    Path temp = directory.resolve("compacting.json~");
    Map<String, Map<String, Object>> finalState = history.states().getLast();
    byte[] compacted = compactedForm(history);
    Random random = new Random(17);
    for (int i = 0; i < 20; i++) {
      // killed while writing the temporary file: the data file is intact and the leftover is ignored
      Files.write(temp, Arrays.copyOf(compacted, random.nextInt(compacted.length)));
      assertThat(load(file, history.bytes())).isEqualTo(finalState);
      // killed after the temporary file was flushed but before the rename replaced the data file
      Files.deleteIfExists(file);
      Files.write(temp, compacted);
      Datastore datastore = open(DatastoreOptions.of(file));
      assertThat(byId(datastore.getAllData())).isEqualTo(finalState);
      datastore.close();
      assertThat(Files.exists(temp)).isFalse();
    }
    assertThat(corruptionWarnings()).isEmpty();
  }

  private byte[] compactedForm(History history) throws IOException {
    Path file = directory.resolve("compacted-form.json");
    Files.write(file, history.bytes());
    Datastore datastore = open(DatastoreOptions.of(file));
    datastore.loadDatabase();
    datastore.close();
    return Files.readAllBytes(file);
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Large datasets
  // -----------------------------------------------------------------------------------------------------------------

  @Test
  void loadsAndQueriesOneHundredThousandEvents() throws IOException {
    Path file = directory.resolve("events.json");
    int entities = 2_000;
    int total = 100_000;
    Datastore writer = open(DatastoreOptions.of(file));
    long writeStart = System.nanoTime();
    List<Map<String, Object>> batch = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      batch.add(event(i, entities));
      if (batch.size() == 5_000) {
        writer.insert(batch);
        batch.clear();
      }
    }
    long writeMillis = (System.nanoTime() - writeStart) / 1_000_000;
    writer.close();
    long fileSize = Files.size(file);

    long loadStart = System.nanoTime();
    Datastore datastore = open(DatastoreOptions.of(file));
    datastore.ensureLookupIndex("entityID");
    datastore.loadDatabase();
    long loadMillis = (System.nanoTime() - loadStart) / 1_000_000;
    assertThat(datastore.size()).isEqualTo(total);

    long queryStart = System.nanoTime();
    List<Map<String, Object>> entityEvents = datastore.find(map("entityID", "entity-42", "kind", "event",
        "createdAt", map("$gt", "1970-01-01T00:00:00.000Z"), "deletedAt", map("$exists", false)))
        .sort(map("createdAt", 1)).exec();
    long entityQueryMillis = (System.nanoTime() - queryStart) / 1_000_000;
    assertThat(entityEvents).hasSize(total / entities);
    assertThat(entityEvents).isSortedAccordingTo((a, b) ->
        ((String) a.get("createdAt")).compareTo((String) b.get("createdAt")));

    long scanStart = System.nanoTime();
    List<Map<String, Object>> ids = datastore.find(map("kind", "event", "entityTypeName", "Cart"),
        map("entityID", 1)).sort(map("createdAt", -1)).exec();
    long scanMillis = (System.nanoTime() - scanStart) / 1_000_000;
    assertThat(ids).hasSize(total);
    assertThat(ids.getFirst()).containsOnlyKeys("entityID", "_id");

    System.out.printf("[store stress] 100k events: %.1f MiB, write %d ms, load+compact %d ms, entity query %d ms, "
        + "full scan+sort %d ms%n", fileSize / 1048576.0, writeMillis, loadMillis, entityQueryMillis, scanMillis);
    // generous bounds: they catch accidental quadratic behaviour, not small regressions
    assertThat(loadMillis).isLessThan(30_000);
    assertThat(entityQueryMillis).isLessThan(2_000);
    assertThat(scanMillis).isLessThan(15_000);
  }

  private static Map<String, Object> event(int i, int entities) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("version", 1);
    event.put("kind", "event");
    event.put("superKind", "domain");
    event.put("requestID", "request-" + i);
    event.put("entityID", "entity-" + (i % entities));
    event.put("entityTypeName", "Cart");
    event.put("typeName", "CartItemChanged");
    event.put("value", map("cartId", "entity-" + (i % entities), "productId", "product-" + (i % 97),
        "quantity", i % 7, "price", map("cents", i * 10, "currency", "EUR")));
    event.put("currentUser", map("username", "user" + (i % 13) + "@example.com", "roles", list("User"),
        "claims", map()));
    event.put("createdAt", String.format("2026-11-10T%02d:%02d:%02d.%03dZ",
        (i / 3_600_000) % 24, (i / 60_000) % 60, (i / 1_000) % 60, i % 1_000));
    return event;
  }

  @Test
  void loadsOneHundredThousandReadModelsWithTheirUniqueIndex() throws IOException {
    Path file = directory.resolve("read_models.json");
    int total = 100_000;
    Datastore writer = open(DatastoreOptions.of(file));
    writer.ensureIndex(IndexOptions.unique("uniqueKey", true));
    List<Map<String, Object>> batch = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      batch.add(map("typeName", "CartReadModel", "uniqueKey", "CartReadModel_" + i + "_1",
          "value", map("id", "cart-" + i, "items", list(map("sku", "s" + (i % 50), "quantity", i % 9)),
              "boosterMetadata", map("version", 1, "schemaVersion", 1))));
      if (batch.size() == 5_000) {
        writer.insert(batch);
        batch.clear();
      }
    }
    writer.close();
    long loadStart = System.nanoTime();
    Datastore datastore = open(DatastoreOptions.of(file));
    datastore.ensureLookupIndex("value.id");
    datastore.loadDatabase();
    long loadMillis = (System.nanoTime() - loadStart) / 1_000_000;
    long casStart = System.nanoTime();
    for (int i = 0; i < 300; i++) {
      UpdateResult result = datastore.update(
          map("typeName", "CartReadModel", "value.id", "cart-" + i, "value.boosterMetadata.version", 1),
          map("typeName", "CartReadModel", "uniqueKey", "CartReadModel_" + i + "_2",
              "value", map("id", "cart-" + i, "boosterMetadata", map("version", 2))),
          new UpdateOptions(false, false, true));
      assertThat(result.numAffected()).isEqualTo(1);
    }
    long casMillis = (System.nanoTime() - casStart) / 1_000_000;
    long searchStart = System.nanoTime();
    List<Map<String, Object>> found = datastore.find(map("typeName", "CartReadModel",
        "value.items", map("$elemMatch", map("sku", "s7")))).sort(map("value.id", -1)).limit(10).exec();
    long searchMillis = (System.nanoTime() - searchStart) / 1_000_000;
    assertThat(found).hasSize(10);
    System.out.printf("[store stress] 100k read models: load+compact %d ms, 300 compare-and-swap updates %d ms, "
        + "filtered search %d ms%n", loadMillis, casMillis, searchMillis);
    long insertStart = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      datastore.insert(map("typeName", "Other", "value", map("id", "other-" + i)));
    }
    double flushedWriteMillis = (System.nanoTime() - insertStart) / 100 / 1_000_000.0;
    System.out.printf("[store stress] one flushed single-document write: %.2f ms%n", flushedWriteMillis);
    assertThat(loadMillis).isLessThan(30_000);
    assertThat(casMillis).isLessThan(20_000);
    try {
      datastore.insert(map("typeName", "CartReadModel", "uniqueKey", "CartReadModel_99999_1"));
      throw new AssertionError("the unique index accepted a duplicate");
    } catch (NedbError e) {
      assertThat(e.errorType()).isEqualTo(NedbError.UNIQUE_VIOLATED);
    }
  }
}
