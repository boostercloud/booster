package io.boostercloud.booster.local;

import io.boostercloud.booster.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocalProviderSafetyTest {
  @TempDir Path temporary;

  @Test void lifecycleLockLivesOutsideDataAndPreventsAdoptionWhileHeld() throws Exception {
    Path data = temporary.resolve("data"); Path lock = LocalProvider.lifecycleLockPath(data);
    assertEquals(temporary, lock.getParent());
    try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE); var ignored = channel.lock()) {
      assertThrows(IOException.class, () -> new LocalProvider(data)); assertFalse(Files.exists(data));
    }
    try (var provider = new LocalProvider(data)) {
      assertTrue(Files.exists(lock));
      assertThrows(IOException.class, () -> new LocalProvider(data));
    }
    assertTrue(Files.exists(lock));
  }
  @Test void rejectsAncestorAndDatabaseSymlinksWithoutTouchingTheirTargets() throws Exception {
    Path target = temporary.resolve("target"); Files.createDirectory(target);
    Path alias = temporary.resolve("alias"); Files.createSymbolicLink(alias, target);
    assertThrows(IOException.class, () -> new LocalProvider(alias.resolve("child"))); assertFalse(Files.exists(target.resolve("child")));
    Path data = temporary.resolve("data");
    try (var ignored = new LocalProvider(data)) {}
    Path outside = temporary.resolve("outside.db"); Files.writeString(outside, "preserve me");
    Files.delete(data.resolve("events.mv.db")); Files.createSymbolicLink(data.resolve("events.mv.db"), outside);
    assertThrows(IOException.class, () -> new LocalProvider(data)); assertEquals("preserve me", Files.readString(outside));
  }
  @Test void workersStopBeforeStoresAndAreNotClosedTwice() throws Exception {
    var calls = new AtomicInteger();
    try (var provider = new LocalProvider(temporary.resolve("data"))) {
      provider.own(() -> {
        calls.incrementAndGet(); provider.stateStore().put("shutdown", "called", 0, Json.MAPPER.createObjectNode().put("ok", true));
      });
      provider.stopWorkers(); provider.stopWorkers();
      assertEquals(1, calls.get()); assertTrue(provider.stateStore().get("shutdown", "called").orElseThrow().value().path("ok").asBoolean());
    }
    assertEquals(1, calls.get());
  }
  @Test void failedWorkerShutdownStillReleasesJournalAndLifecycleLock() throws Exception {
    Path data = temporary.resolve("data");
    var provider = new LocalProvider(data); provider.own(() -> { throw new IOException("worker failure"); });
    assertThrows(IllegalStateException.class, provider::close);
    try (var reopened = new LocalProvider(data)) { assertEquals(0, reopened.eventStore().lastPosition()); }
  }
}
