package io.boostercloud.booster.store;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.CommitResult;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.OptimisticConcurrencyException;
import io.boostercloud.booster.core.PendingEvent;
import io.boostercloud.booster.core.StreamId;
import io.boostercloud.booster.core.UserContext;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class JdbcEventStoreTest {
  @TempDir Path directory;
  private static final StreamId ACCOUNT_A = new StreamId("Account", "a");
  private static final StreamId ACCOUNT_B = new StreamId("Account", "b");

  @Test
  void roundTripsAllEnvelopeFieldsAndDurableReceiptAfterReopen() {
    Path file = directory.resolve("nested/journal");
    PendingEvent first = event("event-1", ACCOUNT_A, 10);
    PendingEvent second = event("event-2", ACCOUNT_A, 20);
    try (var store = new JdbcEventStore(file)) {
      CommitResult committed = store.commit("request-1", "fingerprint", Map.of(ACCOUNT_A, 0L),
          List.of(first, second), json(Map.of("accepted", true)));
      assertFalse(committed.duplicate());
      assertEquals(List.of(1L, 2L), committed.receipt().events().stream().map(e -> e.position()).toList());
      assertEquals(List.of(1L, 2L), committed.receipt().events().stream().map(e -> e.streamVersion()).toList());
    }
    try (var store = new JdbcEventStore(file)) {
      assertEquals(2, store.lastPosition());
      var read = store.readStream(ACCOUNT_A, 0);
      assertEquals(first, read.get(0).event());
      assertEquals(second, read.get(1).event());
      assertEquals(List.of(read.get(1)), store.readStream(ACCOUNT_A, 1));
      var receipt = store.findCommand("request-1").orElseThrow();
      assertEquals(read, receipt.events());
      assertTrue(receipt.result().path("accepted").booleanValue());
      assertEquals("fingerprint", receipt.fingerprint());
      assertEquals("request-1", receipt.requestId());
      assertTrue(store.findCommand("missing").isEmpty());
    }
  }

  @Test
  void duplicateRequestReturnsOriginalResultBeforeCheckingStaleVersions() {
    try (var store = JdbcEventStore.inMemory()) {
      var original = store.commit("r", "f", Map.of(ACCOUNT_A, 0L),
          List.of(event("e", ACCOUNT_A, 1)), json("original"));
      var duplicate = store.commit("r", "f", Map.of(ACCOUNT_A, 0L),
          List.of(event("different-event", ACCOUNT_A, 999)), json("changed"));
      assertTrue(duplicate.duplicate());
      assertEquals(original.receipt(), duplicate.receipt());
      assertEquals(1, store.lastPosition());
      var error = assertThrows(BoosterException.class, () ->
          store.commit("r", "other-fingerprint", Map.of(), List.of(), json(null)));
      assertEquals(BoosterException.Code.CONFLICT, error.code());
      assertEquals(1, store.readStream(ACCOUNT_A, 0).size());
    }
  }

  @Test
  void eventlessCommandStillHasDurableIdempotencyReceiptAndNoForeignEvents() {
    Path file = directory.resolve("empty-command");
    try (var store = new JdbcEventStore(file)) {
      store.commit("empty", "f", Map.of(), List.of(), json(null));
      store.commit("other", "f", Map.of(ACCOUNT_A, 0L), List.of(event("e", ACCOUNT_A, 1)), json(1));
    }
    try (var store = new JdbcEventStore(file)) {
      var receipt = store.findCommand("empty").orElseThrow();
      assertTrue(receipt.result().isNull());
      assertTrue(receipt.events().isEmpty());
      assertTrue(store.commit("empty", "f", Map.of(), List.of(), json("new")).duplicate());
      assertEquals(1, store.lastPosition());
    }
  }

  @Test
  void multistreamConflictLeavesEveryStreamAndReceiptUnchanged() {
    try (var store = JdbcEventStore.inMemory()) {
      store.commit("existing", "f", Map.of(ACCOUNT_B, 0L), List.of(event("b1", ACCOUNT_B, 1)), json(1));
      assertThrows(OptimisticConcurrencyException.class, () ->
          store.commit("failed", "f", Map.of(ACCOUNT_A, 0L, ACCOUNT_B, 0L),
              List.of(event("a1", ACCOUNT_A, 2), event("b2", ACCOUNT_B, 3)), json("partial")));
      assertTrue(store.readStream(ACCOUNT_A, 0).isEmpty());
      assertEquals(1, store.readStream(ACCOUNT_B, 0).size());
      assertTrue(store.findCommand("failed").isEmpty());
      assertEquals(1, store.lastPosition());
      var successful = store.commit("retry", "f", Map.of(ACCOUNT_A, 0L, ACCOUNT_B, 1L),
          List.of(event("a1", ACCOUNT_A, 2), event("b2", ACCOUNT_B, 3)), json("complete"));
      assertEquals(List.of(2L, 3L), successful.receipt().events().stream().map(e -> e.position()).toList());
      assertEquals(List.of(1L, 2L), successful.receipt().events().stream().map(e -> e.streamVersion()).toList());
    }
  }

  @Test
  void uniqueEventFailureRollsBackEarlierInsertsAndJournalPosition() {
    try (var store = JdbcEventStore.inMemory()) {
      store.commit("r1", "f", Map.of(ACCOUNT_A, 0L), List.of(event("duplicate", ACCOUNT_A, 1)), json(1));
      var error = assertThrows(BoosterException.class, () ->
          store.commit("r2", "f", Map.of(ACCOUNT_B, 0L),
              List.of(event("new", ACCOUNT_B, 2), event("duplicate", ACCOUNT_B, 3)), json(2)));
      assertEquals(BoosterException.Code.CONFLICT, error.code());
      assertEquals(1, store.lastPosition());
      assertTrue(store.readStream(ACCOUNT_B, 0).isEmpty());
      assertTrue(store.findCommand("r2").isEmpty());
      assertEquals(2, store.commit("r3", "f", Map.of(ACCOUNT_B, 0L),
          List.of(event("new", ACCOUNT_B, 2)), json(3)).receipt().events().get(0).position());
    }
  }

  @Test
  @Timeout(20)
  void competingInstancesAllowExactlyOneCommitAtAnExpectedVersion() throws Exception {
    String name = "concurrent_" + UUID.randomUUID().toString().replace("-", "");
    try (var first = JdbcEventStore.inMemory(name);
         var second = JdbcEventStore.inMemory(name);
         var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      Callable<Boolean> a = () -> appendConcurrent(first, "a", start);
      Callable<Boolean> b = () -> appendConcurrent(second, "b", start);
      var aResult = executor.submit(a);
      var bResult = executor.submit(b);
      start.countDown();
      assertNotEquals(aResult.get(10, TimeUnit.SECONDS), bResult.get(10, TimeUnit.SECONDS));
      assertEquals(1, first.lastPosition());
      assertEquals(1, second.readStream(ACCOUNT_A, 0).size());
    }
  }

  @Test
  @Timeout(20)
  void concurrentIndependentStreamsProduceConsecutiveCommittedGlobalPositions() throws Exception {
    try (var store = JdbcEventStore.inMemory(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<CommitResult>> writes = new ArrayList<>();
      for (int i = 0; i < 30; i++) {
        int number = i;
        StreamId stream = new StreamId("Account", "account-" + i);
        writes.add(() -> store.commit("request-" + number, "fingerprint", Map.of(stream, 0L),
            List.of(event("event-" + number, stream, number)), json(number)));
      }
      for (var result : executor.invokeAll(writes)) assertFalse(result.get().duplicate());
      assertEquals(30, store.lastPosition());
      assertEquals(java.util.stream.LongStream.rangeClosed(1, 30).boxed().toList(),
          store.readAll(0, 100).stream().map(e -> e.position()).toList());
      assertEquals(List.of(11L, 12L, 13L), store.readAll(10, 3).stream().map(e -> e.position()).toList());
    }
  }

  @Test
  @Timeout(20)
  void concurrentRetryOfSameRequestCommitsOnceAcrossInstances() throws Exception {
    Path file = directory.resolve("deduplicated");
    try (var a = new JdbcEventStore(file); var b = new JdbcEventStore(file);
         var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      Callable<CommitResult> first = () -> {
        start.await();
        return a.commit("same", "f", Map.of(ACCOUNT_A, 0L), List.of(event("first", ACCOUNT_A, 1)), json("first"));
      };
      Callable<CommitResult> second = () -> {
        start.await();
        return b.commit("same", "f", Map.of(ACCOUNT_A, 0L), List.of(event("second", ACCOUNT_A, 2)), json("second"));
      };
      var firstResult = executor.submit(first);
      var secondResult = executor.submit(second);
      start.countDown();
      var firstCommit = firstResult.get(10, TimeUnit.SECONDS);
      var secondCommit = secondResult.get(10, TimeUnit.SECONDS);
      assertNotEquals(firstCommit.duplicate(), secondCommit.duplicate());
      assertEquals(firstCommit.receipt(), secondCommit.receipt());
      assertEquals(1, a.lastPosition());
    }
  }

  @Test
  void returnedJsonCannotMutateStoredHistoryOrResult() {
    try (var store = JdbcEventStore.inMemory()) {
      var receipt = store.commit("r", "f", Map.of(ACCOUNT_A, 0L),
          List.of(event("e", ACCOUNT_A, 1)), json(Map.of("result", 1))).receipt();
      ((com.fasterxml.jackson.databind.node.ObjectNode) receipt.result()).put("result", 999);
      ((com.fasterxml.jackson.databind.node.ObjectNode) receipt.events().get(0).event().payload()).put("amount", 999);
      var read = store.findCommand("r").orElseThrow();
      assertEquals(1, read.result().path("result").intValue());
      assertEquals(1, read.events().get(0).event().payload().path("amount").intValue());
    }
  }

  @Test
  void rejectsInvalidPagingStreamsAndMissingExpectedVersionWithoutWriting() {
    try (var store = JdbcEventStore.inMemory()) {
      assertThrows(IllegalArgumentException.class, () -> store.readAll(-1, 10));
      assertThrows(IllegalArgumentException.class, () -> store.readAll(0, 0));
      assertThrows(IllegalArgumentException.class, () -> store.readAll(0, 10_001));
      assertThrows(IllegalArgumentException.class, () -> store.readStream(ACCOUNT_A, -1));
      assertThrows(IllegalArgumentException.class, () -> store.commit("r", "f", Map.of(), List.of(event("e", ACCOUNT_A, 1)), json(1)));
      assertThrows(IllegalArgumentException.class, () -> store.commit("r", "f", Map.of(ACCOUNT_A, -1L), List.of(), json(1)));
      assertThrows(BoosterException.class, () -> store.commit("r", "f", Map.of(ACCOUNT_A, 0L),
          List.of(event("e", ACCOUNT_A, 1), event("e", ACCOUNT_A, 2)), json(1)));
      assertEquals(0, store.lastPosition());
    }
    assertThrows(IllegalArgumentException.class, () -> JdbcEventStore.inMemory("name;INIT=DROP ALL OBJECTS"));
    assertThrows(IllegalArgumentException.class, () -> new JdbcEventStore(directory.resolve("name;bad")));
  }

  @Test
  void closeIsIdempotentAndDoesNotCloseOtherInstances() {
    String name = "shared_" + UUID.randomUUID().toString().replace("-", "");
    var first = JdbcEventStore.inMemory(name);
    try (var second = JdbcEventStore.inMemory(name)) {
      first.commit("r", "f", Map.of(ACCOUNT_A, 0L), List.of(event("e", ACCOUNT_A, 1)), json(1));
      first.close();
      first.close();
      assertThrows(IllegalStateException.class, first::lastPosition);
      assertEquals(1, second.lastPosition());
    } finally {
      first.close();
    }
  }

  @Test
  void rejectsUnknownFutureSchemaWithoutResettingData() throws Exception {
    Path file = directory.resolve("future");
    try (var store = new JdbcEventStore(file)) {
      store.commit("r", "f", Map.of(ACCOUNT_A, 0L), List.of(event("e", ACCOUNT_A, 1)), json(1));
    }
    String url = "jdbc:h2:file:" + file.toAbsolutePath();
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
      statement.execute("UPDATE booster_schema SET schema_version = 99");
    }
    assertThrows(BoosterException.class, () -> new JdbcEventStore(file));
    try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement();
         var rows = statement.executeQuery("SELECT COUNT(*) FROM booster_events")) {
      assertTrue(rows.next());
      assertEquals(1, rows.getInt(1));
    }
  }

  private static boolean appendConcurrent(JdbcEventStore store, String id, CountDownLatch start) throws Exception {
    start.await();
    try {
      store.commit(id, "f", Map.of(ACCOUNT_A, 0L), List.of(event(id, ACCOUNT_A, 1)), json(id));
      return true;
    } catch (OptimisticConcurrencyException conflict) {
      return false;
    }
  }

  private static PendingEvent event(String id, StreamId stream, int amount) {
    return new PendingEvent(id, stream, "MoneyDeposited", 2, json(Map.of("amount", amount)),
        Instant.parse("2026-11-04T12:34:56.123456Z"),
        new UserContext("user-42", Set.of("customer"), Map.of("tenant", "one")),
        Map.of("traceId", "trace-42"));
  }

  private static JsonNode json(Object value) { return Json.MAPPER.valueToTree(value); }
}
