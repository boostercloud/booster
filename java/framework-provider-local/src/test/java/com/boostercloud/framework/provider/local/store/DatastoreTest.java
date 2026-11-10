package com.boostercloud.framework.provider.local.store;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatastoreTest {

  @TempDir
  Path directory;

  private Path file;
  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
  private final List<Datastore> opened = new ArrayList<>();

  @BeforeEach
  void setUp() {
    file = directory.resolve(".booster").resolve("test.json");
  }

  @AfterEach
  void tearDown() {
    opened.forEach(Datastore::close);
  }

  private Datastore open() {
    return open(DatastoreOptions.of(file));
  }

  private Datastore open(DatastoreOptions options) {
    Datastore datastore = new Datastore(options.withWarnings(warnings::add));
    opened.add(datastore);
    return datastore;
  }

  private List<String> lines() throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8);
  }

  private void writeFile(String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  @Nested
  class Crud {
    @Test
    void insertGeneratesNedbIdsWithoutTouchingTheCallerDocument() {
      Datastore datastore = open();
      Map<String, Object> document = map("a", 1);
      Map<String, Object> stored = datastore.insert(document);
      assertThat(document).doesNotContainKey("_id");
      assertThat((String) stored.get("_id")).matches("[0-9A-Za-z]{16}");
      assertThat(datastore.find(map()).exec()).containsExactly(stored);
    }

    @Test
    void generatedIdsGrowSoUnsortedResultsComeInInsertionOrder() {
      Datastore datastore = open();
      List<String> ids = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        ids.add((String) datastore.insert(map("i", i)).get("_id"));
      }
      List<String> sorted = new ArrayList<>(ids);
      Collections.sort(sorted);
      assertThat(sorted).isEqualTo(ids);
      assertThat(datastore.find(map()).exec().stream().map(d -> d.get("i")).toList())
          .isEqualTo(java.util.stream.IntStream.range(0, 200).boxed().toList());
    }

    @Test
    void resultsAreDeepCopies() {
      Datastore datastore = open();
      datastore.insert(map("_id", "1", "o", map("x", 1)));
      Map<String, Object> result = datastore.find(map()).exec().get(0);
      ((Map<String, Object>) result.get("o")).put("x", 2);
      assertThat(datastore.find(map()).exec().get(0).get("o")).isEqualTo(map("x", 1));
    }

    @Test
    void duplicateIdsAreRejected() {
      Datastore datastore = open();
      datastore.insert(map("_id", "same"));
      assertThatThrownBy(() -> datastore.insert(map("_id", "same")))
          .isInstanceOf(NedbError.class)
          .hasMessage("Can't insert key \"same\", it violates the unique constraint")
          .extracting(e -> ((NedbError) e).errorType()).isEqualTo("uniqueViolated");
    }

    @Test
    void invalidFieldNamesAreRejectedAndNothingIsStored() {
      Datastore datastore = open();
      assertThatThrownBy(() -> datastore.insert(map("a.b", 1))).hasMessage("Field names cannot contain a .");
      assertThat(datastore.count(null)).isZero();
    }

    @Test
    void insertingSeveralDocumentsIsAllOrNothing() {
      Datastore datastore = open();
      datastore.insert(map("_id", "b"));
      assertThatThrownBy(() -> datastore.insert(List.of(map("_id", "a"), map("_id", "b"))))
          .isInstanceOf(NedbError.class);
      assertThat(datastore.count(null)).isEqualTo(1);
      datastore.insert(List.of(map("_id", "c"), map("_id", "d")));
      assertThat(datastore.count(null)).isEqualTo(3);
    }

    @Test
    void updateReplacesTheFirstMatchOrAllWithMulti() {
      Datastore datastore = open();
      datastore.insert(map("_id", "1", "k", "x", "v", 1));
      datastore.insert(map("_id", "2", "k", "x", "v", 2));
      UpdateResult one = datastore.update(map("k", "x"), map("k", "y"), new UpdateOptions(false, false, true));
      assertThat(one.numAffected()).isEqualTo(1);
      assertThat(one.affectedDocuments()).isEqualTo(map("k", "y", "_id", "1"));
      assertThat(datastore.count(map("k", "x"))).isEqualTo(1);
      UpdateResult all = datastore.update(map(), map("$set", map("z", 1)), new UpdateOptions(true, false, true));
      assertThat(all.numAffected()).isEqualTo(2);
      assertThat((List<?>) all.affectedDocuments()).hasSize(2);
      assertThat(datastore.update(map("k", "none"), map("k", "w"), UpdateOptions.DEFAULTS).numAffected()).isZero();
    }

    @Test
    void upsertInsertsWhenNothingMatches() {
      Datastore datastore = open();
      UpdateResult replacement = datastore.update(map("k", "a"), map("k", "a", "v", 1),
          new UpdateOptions(false, true, false));
      assertThat(replacement.upsert()).isTrue();
      UpdateResult modifiers = datastore.update(map("k", "b", "n", map("$gt", 1)), map("$set", map("v", 2)),
          new UpdateOptions(false, true, false));
      assertThat(modifiers.affectedDocuments()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
          .containsEntry("k", "b").containsEntry("v", 2).containsEntry("n", map());
      assertThat(datastore.update(map("k", "a"), map("$set", map("v", 3)), new UpdateOptions(false, true, false))
          .upsert()).isFalse();
      assertThat(datastore.count(null)).isEqualTo(2);
    }

    @Test
    void removeOneOrMany() {
      Datastore datastore = open();
      for (int i = 0; i < 5; i++) {
        datastore.insert(map("k", i % 2));
      }
      assertThat(datastore.remove(map("k", 0), false)).isEqualTo(1);
      assertThat(datastore.remove(map("k", 0), true)).isEqualTo(2);
      assertThat(datastore.remove(map(), true)).isEqualTo(2);
      assertThat(datastore.count(null)).isZero();
    }
  }

  @Nested
  class Cursors {
    private Datastore datastore;

    @BeforeEach
    void seed() {
      datastore = open();
      datastore.insert(map("_id", "a", "n", 3, "s", "b", "o", map("x", 2)));
      datastore.insert(map("_id", "b", "n", 1, "s", "a", "o", map("x", 1)));
      datastore.insert(map("_id", "c", "n", 2, "s", "a"));
      datastore.insert(map("_id", "d", "s", "c", "o", map("x", 3)));
    }

    private List<Object> ids(List<Map<String, Object>> documents) {
      return documents.stream().map(d -> d.get("_id")).toList();
    }

    @Test
    void sortsByOneOrMoreDotPathsWithMissingValuesFirst() {
      assertThat(ids(datastore.find(map()).sort(map("n", 1)).exec())).containsExactly("d", "b", "c", "a");
      assertThat(ids(datastore.find(map()).sort(map("n", -1)).exec())).containsExactly("a", "c", "b", "d");
      assertThat(ids(datastore.find(map()).sort(map("s", 1, "n", -1)).exec())).containsExactly("c", "b", "a", "d");
      assertThat(ids(datastore.find(map()).sort(map("o.x", -1)).exec())).containsExactly("d", "a", "b", "c");
    }

    @Test
    void sortTiesKeepIdOrder() {
      assertThat(ids(datastore.find(map()).sort(map("missing", 1)).exec())).containsExactly("a", "b", "c", "d");
    }

    @Test
    void skipAndLimitWithAndWithoutSort() {
      assertThat(ids(datastore.find(map()).skip(1).limit(2).exec())).containsExactly("b", "c");
      assertThat(ids(datastore.find(map()).sort(map("n", 1)).skip(1).limit(2).exec())).containsExactly("b", "c");
      assertThat(ids(datastore.find(map()).limit(0).exec())).hasSize(4);
      assertThat(ids(datastore.find(map()).skip(Double.NaN).exec())).hasSize(4);
    }

    @Test
    void projections() {
      assertThat(datastore.find(map("_id", "a"), map("n", 1)).exec()).containsExactly(map("n", 3, "_id", "a"));
      assertThat(datastore.find(map("_id", "a"), map("n", 1, "o.x", 1, "_id", 0)).exec())
          .containsExactly(map("n", 3, "o", map("x", 2)));
      assertThat(datastore.find(map("_id", "c"), map("n", 0, "s", 0)).exec()).containsExactly(map("_id", "c"));
      assertThat(datastore.find(map("_id", "d"), map("n", 1)).exec()).containsExactly(map("_id", "d"));
      assertThatThrownBy(() -> datastore.find(map(), map("n", 1, "s", 0)).exec())
          .isInstanceOf(NedbError.class).hasMessage("Can't both keep and omit fields except for _id");
    }

    @Test
    void countAndIdLookups() {
      assertThat(datastore.count(map("s", "a"))).isEqualTo(2);
      assertThat(datastore.count(map("_id", map("$in", list("d", "a", "zz"))))).isEqualTo(2);
      assertThat(ids(datastore.find(map("_id", map("$in", list("d", "a")))).exec())).containsExactly("d", "a");
      assertThat(ids(datastore.find(map("_id", map("$gt", "a", "$lte", "c"))).exec())).containsExactly("b", "c");
    }
  }

  @Nested
  class Indexes {
    @Test
    void uniqueSparseIndexesRejectDuplicatesButNotMissingFields() {
      Datastore datastore = open();
      datastore.ensureIndex(IndexOptions.unique("uniqueKey", true));
      datastore.insert(map("uniqueKey", "k1"));
      datastore.insert(map("other", 1));
      datastore.insert(map("other", 2));
      assertThatThrownBy(() -> datastore.insert(map("uniqueKey", "k1")))
          .isInstanceOf(NedbError.class)
          .hasMessage("Can't insert key \"k1\", it violates the unique constraint");
      assertThat(datastore.count(null)).isEqualTo(3);
    }

    @Test
    void uniqueNonSparseIndexesTreatMissingFieldsAsOneKey() {
      Datastore datastore = open();
      datastore.ensureIndex(new IndexOptions("k", true, null, null));
      datastore.insert(map("a", 1));
      assertThatThrownBy(() -> datastore.insert(map("a", 2)))
          .hasMessage("Can't insert key undefined, it violates the unique constraint");
    }

    @Test
    void creatingAUniqueIndexOverDuplicatesFails() {
      Datastore datastore = open();
      datastore.insert(map("k", 1));
      datastore.insert(map("k", 1));
      assertThatThrownBy(() -> datastore.ensureIndex(IndexOptions.unique("k", false))).isInstanceOf(NedbError.class);
      assertThat(datastore.indexNames()).containsExactly("_id");
    }

    @Test
    void updatesThatViolateAUniqueIndexChangeNothing() {
      Datastore datastore = open();
      datastore.ensureIndex(IndexOptions.unique("k", true));
      datastore.insert(map("_id", "1", "k", "a"));
      datastore.insert(map("_id", "2", "k", "b"));
      assertThatThrownBy(() -> datastore.update(map("_id", "2"), map("k", "a"), UpdateOptions.DEFAULTS))
          .extracting(e -> ((NedbError) e).errorType()).isEqualTo("uniqueViolated");
      assertThat(datastore.find(map("_id", "2")).exec().get(0).get("k")).isEqualTo("b");
      datastore.update(map("_id", "2"), map("k", "c"), UpdateOptions.DEFAULTS);
      datastore.insert(map("k", "b"));
    }

    @Test
    void indexedArrayFieldsIndexEachElement() {
      Datastore datastore = open();
      datastore.ensureIndex(IndexOptions.unique("tags", true));
      datastore.insert(map("tags", list("a", "b", "a")));
      assertThatThrownBy(() -> datastore.insert(map("tags", list("c", "b")))).isInstanceOf(NedbError.class);
      assertThat(datastore.find(map("tags", "a")).exec()).hasSize(1);
      datastore.insert(map("tags", list("c")));
    }

    @Test
    void ttlIndexesExpireDatesButNotNumbers() {
      AtomicLong now = new AtomicLong(1_000_000);
      Datastore datastore = open(DatastoreOptions.of(file).withClock(now::get));
      datastore.ensureIndex(IndexOptions.ttl("expirationTime", 10));
      datastore.insert(map("_id", "date", "expirationTime", new NedbDate(1_000_000)));
      datastore.insert(map("_id", "number", "expirationTime", 1));
      assertThat(datastore.count(null)).isEqualTo(2);
      now.addAndGet(10_001);
      assertThat(datastore.find(map()).exec()).extracting(d -> d.get("_id")).containsExactly("number");
      assertThat(datastore.size()).isEqualTo(1);
    }

    @Test
    void ensureIndexIsIdempotentAndPersisted() throws IOException {
      Datastore datastore = open();
      datastore.ensureIndex(IndexOptions.unique("uniqueKey", true));
      datastore.ensureIndex(IndexOptions.unique("uniqueKey", true));
      assertThat(lines()).containsExactly("{\"$$indexCreated\":{\"fieldName\":\"uniqueKey\",\"unique\":true,\"sparse\":true}}");
      datastore.removeIndex("uniqueKey");
      assertThat(lines()).last().isEqualTo("{\"$$indexRemoved\":\"uniqueKey\"}");
      assertThatThrownBy(() -> datastore.ensureIndex(IndexOptions.of("")))
          .hasMessage("Cannot create an index without a fieldName");
      assertThatThrownBy(() -> datastore.ensureIndex(IndexOptions.of("a,b"))).hasMessage("Cannot use comma in index fieldName");
    }

    @Test
    void lookupIndexesNeverChangeResults() {
      Datastore plain = open(DatastoreOptions.inMemory());
      Datastore indexed = open(DatastoreOptions.inMemory());
      indexed.ensureLookupIndex("k");
      indexed.ensureLookupIndex("value.id");
      List<Map<String, Object>> documents = List.of(
          map("_id", "1", "k", "a"), map("_id", "2", "k", list("a", list("b"))), map("_id", "3", "k", null),
          map("_id", "4"), map("_id", "5", "k", 1), map("_id", "6", "k", 1.0),
          map("_id", "7", "value", list(map("id", "x"), map("id", "y"))), map("_id", "8", "value", map("id", "x")));
      plain.insert(documents);
      indexed.insert(documents);
      indexed.update(map("_id", "1"), map("k", "b"), UpdateOptions.DEFAULTS);
      plain.update(map("_id", "1"), map("k", "b"), UpdateOptions.DEFAULTS);
      for (Map<String, Object> query : List.of(map("k", "a"), map("k", "b"), map("k", null), map("k", 1),
          map("value.id", "x"), map("value.id", "y"), map("k", "zz"), map("k", 1, "_id", map("$ne", "5")))) {
        assertThat(indexed.find(query).exec()).as("query %s", query).isEqualTo(plain.find(query).exec());
      }
    }
  }

  @Nested
  class Persistence {
    @Test
    void writesTheNedbFormat() throws IOException {
      Datastore datastore = open();
      datastore.insert(map("_id", "id1", "a", 1));
      datastore.update(map("_id", "id1"), map("a", 2), UpdateOptions.DEFAULTS);
      datastore.insert(map("_id", "id2", "b", true));
      datastore.remove(map("_id", "id2"), false);
      assertThat(lines()).containsExactly(
          "{\"_id\":\"id1\",\"a\":1}",
          "{\"a\":2,\"_id\":\"id1\"}",
          "{\"_id\":\"id2\",\"b\":true}",
          "{\"$$deleted\":true,\"_id\":\"id2\"}");
      assertThat(Files.readString(file)).endsWith("\n");
    }

    @Test
    void loadReplaysTheFileAndCompactsIt() throws IOException {
      writeFile("""
          {"_id":"b","v":1}
          {"_id":"a","v":1}
          {"_id":"b","v":2}
          {"$$deleted":true,"_id":"a"}
          {"$$indexCreated":{"fieldName":"v","unique":false,"sparse":false}}
          {"_id":"c","v":3}
          """);
      Datastore datastore = open();
      assertThat(datastore.find(map()).exec()).containsExactly(map("_id", "b", "v", 2), map("_id", "c", "v", 3));
      assertThat(datastore.indexNames()).containsExactly("_id", "v");
      assertThat(lines()).containsExactly(
          "{\"_id\":\"b\",\"v\":2}",
          "{\"_id\":\"c\",\"v\":3}",
          "{\"$$indexCreated\":{\"fieldName\":\"v\",\"unique\":false,\"sparse\":false}}");
      assertThat(Files.exists(file.resolveSibling("test.json~"))).isFalse();
    }

    @Test
    void reopeningSeesEveryAcknowledgedWrite() {
      Datastore first = open();
      first.ensureIndex(IndexOptions.unique("k", true));
      for (int i = 0; i < 50; i++) {
        first.insert(map("_id", "d" + i, "k", "k" + i));
      }
      first.update(map("_id", "d3"), map("$set", map("x", 1)), UpdateOptions.DEFAULTS);
      first.remove(map("_id", "d4"), false);
      first.close();
      Datastore second = open();
      assertThat(second.count(null)).isEqualTo(49);
      assertThat(second.find(map("_id", "d3")).exec().get(0)).containsEntry("x", 1);
      assertThatThrownBy(() -> second.insert(map("k", "k1"))).isInstanceOf(NedbError.class);
    }

    @Test
    void discardsATruncatedLastLine() throws IOException {
      writeFile("{\"_id\":\"a\",\"v\":1}\n{\"_id\":\"b\",\"v\":2}\n{\"_id\":\"c\",\"v\"");
      Datastore datastore = open();
      assertThat(datastore.find(map()).exec()).extracting(d -> d.get("_id")).containsExactly("a", "b");
      assertThat(warnings).anyMatch(w -> w.contains("incomplete last line"));
      assertThat(lines()).hasSize(2);
      datastore.insert(map("_id", "c", "v", 3));
      assertThat(lines()).last().isEqualTo("{\"_id\":\"c\",\"v\":3}");
    }

    @Test
    void aTruncatedLastLineDoesNotCountAsCorruptionEvenInASmallFile() throws IOException {
      writeFile("{\"_id\":\"a\"}\n{\"_id\":\"b");
      assertThat(open().count(null)).isEqualTo(1);
    }

    @Test
    void acceptsAValidLastLineWithoutNewline() throws IOException {
      writeFile("{\"_id\":\"a\"}\n{\"_id\":\"b\"}");
      assertThat(open().count(null)).isEqualTo(2);
    }

    @Test
    void skipsCorruptLinesBelowTheThreshold() throws IOException {
      StringBuilder content = new StringBuilder();
      for (int i = 0; i < 10; i++) {
        content.append("{\"_id\":\"d").append(i).append("\"}\n");
      }
      content.append("not json\n");
      writeFile(content.toString());
      Datastore datastore = open();
      assertThat(datastore.count(null)).isEqualTo(10);
      assertThat(warnings).anyMatch(w -> w.contains("Skipped 1 corrupt line"));
    }

    @Test
    void refusesToLoadAboveTheCorruptAlertThreshold() throws IOException {
      writeFile("{\"_id\":\"a\"}\n{\"_id\":\"b\"}\n{\"_id\":\"c\"}\ngarbage\nnull\n");
      Datastore datastore = open();
      assertThatThrownBy(datastore::loadDatabase)
          .isInstanceOf(CorruptDataFileException.class)
          .hasMessage("40% of the data file is corrupt, more than given corruptAlertThreshold (10%). Cautiously "
              + "refusing to start NeDB to prevent dataloss.")
          .satisfies(e -> {
            CorruptDataFileException corrupt = (CorruptDataFileException) e;
            assertThat(corrupt.corruptItems()).isEqualTo(2);
            assertThat(corrupt.dataLength()).isEqualTo(5);
          });
      assertThat(datastore.isLoaded()).isFalse();
      assertThat(Files.readString(file)).contains("garbage");
      Datastore lenient = open(DatastoreOptions.of(file).withCorruptAlertThreshold(0.5));
      assertThat(lenient.count(null)).isEqualTo(3);
    }

    @Test
    void ignoresLinesThatAreNotDocuments() throws IOException {
      writeFile("{\"no\":\"id\"}\n5\n[1]\n\"s\"\n{\"_id\":\"\"}\n{\"_id\":\"x\"}\n\n");
      assertThat(open().find(map()).exec()).containsExactly(map("_id", "x"));
    }

    @Test
    void recoversTheTemporaryFileOfAnInterruptedCompaction() throws IOException {
      Files.createDirectories(file.getParent());
      Files.writeString(file.resolveSibling("test.json~"), "{\"_id\":\"a\"}\n");
      assertThat(open().count(null)).isEqualTo(1);
      assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void ignoresALeftoverTemporaryFileWhenTheDataFileExists() throws IOException {
      writeFile("{\"_id\":\"a\"}\n");
      Files.writeString(file.resolveSibling("test.json~"), "{\"_id\":\"stale\"}\n");
      assertThat(open().find(map()).exec()).containsExactly(map("_id", "a"));
    }

    @Test
    void explicitAndGrowthTriggeredCompaction() throws IOException {
      Datastore datastore = open();
      datastore.insert(map("_id", "a", "v", 0));
      for (int i = 1; i <= 10; i++) {
        datastore.update(map("_id", "a"), map("v", i), UpdateOptions.DEFAULTS);
      }
      assertThat(lines()).hasSize(11);
      assertThat(datastore.obsoleteLines()).isEqualTo(10);
      datastore.compactDatafile();
      assertThat(lines()).containsExactly("{\"v\":10,\"_id\":\"a\"}");
      assertThat(datastore.obsoleteLines()).isZero();

      Datastore auto = open(DatastoreOptions.of(directory.resolve("auto.json")).withAutocompactionLineThreshold(5));
      auto.insert(map("_id", "a", "v", 0));
      for (int i = 1; i <= 5; i++) {
        auto.update(map("_id", "a"), map("v", i), UpdateOptions.DEFAULTS);
      }
      assertThat(Files.readAllLines(directory.resolve("auto.json"))).containsExactly("{\"v\":5,\"_id\":\"a\"}");
    }

    @Test
    void aRemovalThatTriggersTheGrowthCompactionIsOnDiskToo() {
      // the dispatch journal's lifecycle: insert, $set, remove. The remove that crosses the threshold compacts the
      // file; the compacted file must already be without the removed document (and stay so after a reload)
      Path journal = directory.resolve("journal.json");
      Datastore datastore = open(DatastoreOptions.of(journal).withAutocompactionLineThreshold(10));
      for (int i = 0; i < 7; i++) {
        String id = "id" + i;
        datastore.insert(map("_id", id, "state", "pending"));
        datastore.update(map("_id", id), map("$set", map("state", "dispatched")), UpdateOptions.DEFAULTS);
        datastore.remove(map("_id", id), false);
        assertThat(datastore.size()).isZero();
        Datastore reloaded = open(DatastoreOptions.of(journal));
        assertThat(reloaded.getAllData()).as("file after step %d", i).isEmpty();
        reloaded.close();
      }
    }

    @Test
    void aFailedRemovalKeepsTheDocumentsAndTheirIndexes() throws IOException {
      Path data = directory.resolve("remove.json");
      Datastore store = open(DatastoreOptions.of(data));
      store.ensureIndex(IndexOptions.unique("k", true));
      store.insert(map("_id", "a", "k", "x"));
      store.compactDatafile();
      data.toFile().setWritable(false);
      try {
        org.junit.jupiter.api.Assumptions.assumeFalse(data.toFile().canWrite(), "running as root");
        assertThatThrownBy(() -> store.remove(map("_id", "a"), false)).isInstanceOf(NedbError.class);
        assertThat(store.find(map("k", "x")).exec()).containsExactly(map("_id", "a", "k", "x"));
      } finally {
        data.toFile().setWritable(true);
      }
      assertThatThrownBy(() -> store.insert(map("_id", "b", "k", "x"))).isInstanceOf(NedbError.class)
          .hasMessageContaining("unique constraint");
      assertThat(store.remove(map("k", "x"), false)).isEqualTo(1);
      assertThat(store.size()).isZero();
    }

    @Test
    void periodicCompaction() throws IOException {
      Datastore datastore = open();
      datastore.insert(map("_id", "a"));
      datastore.remove(map("_id", "a"), false);
      datastore.setAutocompactionInterval(Duration.ofMillis(10));
      org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> lines().isEmpty());
      datastore.stopAutocompaction();
    }

    @Test
    void aFailedWriteChangesNothing() throws IOException {
      Datastore datastore = open();
      datastore.insert(map("_id", "a"));
      datastore.close();
      assertThatThrownBy(() -> datastore.insert(map("_id", "b"))).isInstanceOf(NedbError.class)
          .hasMessageContaining("is closed");
      Path readOnlyFile = directory.resolve("data.json");
      Datastore store = open(DatastoreOptions.of(readOnlyFile));
      store.ensureIndex(IndexOptions.unique("k", true));
      store.insert(map("_id", "a", "k", "x"));
      // compaction closes the append channel; the next write has to reopen the (now read-only) file
      store.compactDatafile();
      readOnlyFile.toFile().setWritable(false);
      try {
        org.junit.jupiter.api.Assumptions.assumeFalse(readOnlyFile.toFile().canWrite(), "running as root");
        assertThatThrownBy(() -> store.insert(map("_id", "b", "k", "y"))).isInstanceOf(NedbError.class)
            .hasMessageContaining("Could not write");
        assertThatThrownBy(() -> store.update(map("_id", "a"), map("k", "z"), UpdateOptions.DEFAULTS))
            .isInstanceOf(NedbError.class);
        assertThatThrownBy(() -> store.remove(map("_id", "a"), false)).isInstanceOf(NedbError.class);
        assertThat(store.find(map()).exec()).containsExactly(map("_id", "a", "k", "x"));
      } finally {
        readOnlyFile.toFile().setWritable(true);
      }
      // the in-memory state was rolled back, including the unique index
      store.insert(map("_id", "b", "k", "y"));
      assertThat(store.count(null)).isEqualTo(2);
    }

    @Test
    void inMemoryDatastoresWriteNothing() {
      Datastore datastore = open(DatastoreOptions.inMemory());
      datastore.insert(map("a", 1));
      assertThat(datastore.count(null)).isEqualTo(1);
      assertThat(datastore.filename()).isNull();
    }
  }

  /**
   * Write failures injected into the real files ({@link FaultInjectingChannels}), and the flushes of every write.
   * Whether a flushed write survives a power loss is up to the device and cannot be tested here; what is pinned is
   * that every acknowledged write is flushed before the call returns.
   */
  @Nested
  class WriteFailuresAndFlushes {
    private final FaultInjectingChannels channels = new FaultInjectingChannels();

    private Datastore openFaulty() {
      Datastore datastore = new Datastore(DatastoreOptions.of(file).withWarnings(warnings::add), channels);
      opened.add(datastore);
      return datastore;
    }

    private List<String> operations() {
      return channels.log.stream().map(entry -> entry.replaceAll(" \\d+$", "")).toList();
    }

    @Test
    void anAppendThatFailsHalfwayIsCutBackSoTheNextOneStartsOnItsOwnLine() throws IOException {
      Datastore datastore = openFaulty();
      datastore.insert(map("_id", "a", "v", 1));
      long sizeBefore = Files.size(file);
      channels.log.clear();
      channels.failNextWriteAfter(7);
      assertThatThrownBy(() -> datastore.insert(map("_id", "b", "v", 2))).isInstanceOf(NedbError.class)
          .hasMessageContaining("No space left on device");
      assertThat(channels.log).containsExactly("partial-write test.json 7", "truncate test.json " + sizeBefore,
          "force test.json false");
      assertThat(Files.size(file)).isEqualTo(sizeBefore);
      assertThat(datastore.find(map()).exec()).containsExactly(map("_id", "a", "v", 1));

      datastore.insert(map("_id", "c", "v", 3));
      assertThat(lines()).containsExactly("{\"_id\":\"a\",\"v\":1}", "{\"_id\":\"c\",\"v\":3}");
      assertThat(open().find(map()).exec()).extracting(d -> d.get("_id")).containsExactly("a", "c");
      assertThat(warnings).isEmpty();
    }

    @Test
    void whenAFailedAppendCannotBeCutBackTheStoreRefusesWritesUntilItIsReloaded() {
      Datastore datastore = openFaulty();
      datastore.insert(map("_id", "a"));
      channels.failNextWriteAfter(3);
      channels.failTruncate(true);
      assertThatThrownBy(() -> datastore.insert(map("_id", "b"))).isInstanceOf(NedbError.class);
      channels.failTruncate(false);
      assertThatThrownBy(() -> datastore.insert(map("_id", "c"))).isInstanceOf(NedbError.class)
          .hasMessageContaining("could not be repaired after a failed write");
      assertThat(datastore.find(map()).exec()).containsExactly(map("_id", "a"));

      datastore.loadDatabase();
      assertThat(warnings).anyMatch(w -> w.contains("incomplete last line"));
      datastore.insert(map("_id", "c"));
      assertThat(open().find(map()).exec()).extracting(d -> d.get("_id")).containsExactly("a", "c");
    }

    @Test
    void everyWriteIsFlushedBeforeTheCallReturns() {
      Datastore datastore = openFaulty();
      datastore.insert(map("_id", "a"));
      channels.log.clear();
      datastore.insert(map("_id", "b"));
      datastore.update(map("_id", "b"), map("$set", map("x", 1)), UpdateOptions.DEFAULTS);
      datastore.remove(map("_id", "a"), false);
      datastore.ensureIndex(IndexOptions.unique("x", true));
      assertThat(operations()).containsExactly(
          "write test.json", "force test.json false",
          "write test.json", "force test.json false",
          "write test.json", "force test.json false",
          "write test.json", "force test.json false");
    }

    @Test
    void compactionFlushesTheNewFileBeforeItReplacesTheOldOne() throws IOException {
      Datastore datastore = openFaulty();
      datastore.insert(map("_id", "a"));
      datastore.insert(map("_id", "b"));
      datastore.remove(map("_id", "a"), false);
      channels.log.clear();
      datastore.compactDatafile();
      assertThat(operations()).containsExactly("write test.json~", "force test.json~ true");
      assertThat(lines()).containsExactly("{\"_id\":\"b\"}");
    }
  }

  @Nested
  class Concurrency {
    @Test
    void concurrentVirtualThreadWritersLoseNothing() throws Exception {
      Datastore datastore = open();
      datastore.ensureIndex(IndexOptions.unique("k", true));
      int writers = 32;
      int perWriter = 40;
      CountDownLatch start = new CountDownLatch(1);
      ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int w = 0; w < writers; w++) {
          int writer = w;
          executor.submit(() -> {
            try {
              start.await();
              for (int i = 0; i < perWriter; i++) {
                datastore.insert(map("k", writer + "-" + i, "writer", writer));
                datastore.count(map("writer", writer));
              }
            } catch (Throwable e) {
              errors.add(e);
            }
            return null;
          });
        }
        start.countDown();
      }
      assertThat(errors).isEmpty();
      assertThat(datastore.count(null)).isEqualTo(writers * perWriter);
      datastore.close();
      assertThat(open().count(null)).isEqualTo(writers * perWriter);
    }

    @Test
    void compareAndSwapUpdatesAreAtomic() throws Exception {
      Datastore datastore = open();
      datastore.insert(map("_id", "counter", "version", 0));
      int threads = 16;
      int increments = 25;
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int t = 0; t < threads; t++) {
          executor.submit(() -> {
            int done = 0;
            while (done < increments) {
              Object version = datastore.find(map("_id", "counter")).exec().get(0).get("version");
              int current = ((Number) version).intValue();
              UpdateResult result = datastore.update(map("_id", "counter", "version", current),
                  map("version", current + 1), UpdateOptions.DEFAULTS);
              if (result.numAffected() == 1) {
                done++;
              }
            }
            return null;
          });
        }
      }
      assertThat(datastore.find(map("_id", "counter")).exec().get(0).get("version")).isEqualTo(threads * increments);
    }

    @Test
    void readersSeeConsistentSnapshotsWhileWritersRun() throws Exception {
      Datastore datastore = open();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        executor.submit(() -> {
          for (int i = 0; i < 300; i++) {
            datastore.insert(List.of(map("pair", i), map("pair", i)));
          }
          return null;
        });
        for (int r = 0; r < 4; r++) {
          executor.submit(() -> {
            try {
              for (int i = 0; i < 100; i++) {
                List<Map<String, Object>> all = datastore.find(map()).exec();
                Set<Object> seen = new HashSet<>();
                for (Map<String, Object> document : all) {
                  seen.add(document.get("pair"));
                }
                assertThat(all.size()).isEqualTo(seen.size() * 2);
              }
            } catch (Throwable e) {
              failure.set(e);
            }
            return null;
          });
        }
      }
      assertThat(failure.get()).isNull();
    }
  }
}
