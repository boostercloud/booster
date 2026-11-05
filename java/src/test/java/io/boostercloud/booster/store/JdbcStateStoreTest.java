package io.boostercloud.booster.store;

import io.boostercloud.booster.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcStateStoreTest {
  @Test void durableCasPaginationAndTombstonesPreventAba(@TempDir Path directory) {
    Path path = directory.resolve("state");
    try (var first = new JdbcStateStore(path); var second = new JdbcStateStore(path)) {
      var value = Json.MAPPER.createObjectNode().put("counter", 1);
      first.put("jobs", "a", 0, value); value.put("counter", 99);
      assertEquals(1, second.get("jobs", "a").orElseThrow().value().path("counter").asInt());
      assertThrows(OptimisticConcurrencyException.class, () -> second.put("jobs", "a", 0, value));
      second.delete("jobs", "a", 1);
      var tombstone = first.get("jobs", "a").orElseThrow();
      assertTrue(tombstone.deleted()); assertEquals(2, tombstone.version());
      assertThrows(OptimisticConcurrencyException.class, () -> first.put("jobs", "a", 0, value));
      first.put("jobs", "b", 0, value); first.put("jobs", "c", 0, value);
      assertEquals(List.of("a", "b"), first.scan("jobs", null, 2).stream().map(StateDocument::key).toList());
      assertEquals(List.of("c"), second.scan("jobs", "b", 2).stream().map(StateDocument::key).toList());
    }
    try (var reopened = new JdbcStateStore(path)) { assertEquals(2, reopened.get("jobs", "a").orElseThrow().version()); }
  }
  @Test void competingInstancesHaveExactlyOneCasWinner() throws Exception {
    String name = "race_" + UUID.randomUUID().toString().replace("-", "");
    try (var first = JdbcStateStore.inMemory(name); var second = JdbcStateStore.inMemory(name); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      first.put("models", "one", 0, Json.MAPPER.valueToTree(0));
      var start = new CountDownLatch(1);
      Callable<Boolean> updateFirst = () -> { start.await(); try { first.put("models", "one", 1, Json.MAPPER.valueToTree(1)); return true; } catch (OptimisticConcurrencyException expected) { return false; } };
      Callable<Boolean> updateSecond = () -> { start.await(); try { second.put("models", "one", 1, Json.MAPPER.valueToTree(2)); return true; } catch (OptimisticConcurrencyException expected) { return false; } };
      var a = executor.submit(updateFirst); var b = executor.submit(updateSecond); start.countDown();
      assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
      assertEquals(2, first.get("models", "one").orElseThrow().version());
    }
  }
}
