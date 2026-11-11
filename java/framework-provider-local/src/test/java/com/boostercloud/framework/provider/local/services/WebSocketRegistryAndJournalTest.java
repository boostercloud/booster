package com.boostercloud.framework.provider.local.services;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.store.DatastoreOptions;
import com.boostercloud.framework.provider.local.store.HookedDatastore;
import com.boostercloud.framework.provider.local.store.NedbDate;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests of {@code web-socket-registry.ts} and of the Java-only dispatch journal. */
class WebSocketRegistryAndJournalTest {

  @TempDir
  Path project;

  @Test
  void storesQueriesAndDeletesDocuments() throws Exception {
    Path file = project.resolve(".booster/connections.json");
    WebSocketRegistry registry = new WebSocketRegistry(file);
    try {
      registry.store(ConnectionData.of(new ConnectionDataEnvelope(1, null), "c1"));
      registry.store(ConnectionData.of(new ConnectionDataEnvelope(2, null), "c1"));
      registry.store(map("connectionID", "c2", "expirationTime", 3));
      assertThat(registry.count()).isEqualTo(3);
      assertThat(registry.query(map("connectionID", "c1"))).extracting(d -> d.get("expirationTime"))
          .containsExactly(1, 2);
      assertThat(registry.query(map(), 1, 1, null)).hasSize(1);
      assertThat(registry.delete(map("connectionID", "c1"))).isEqualTo(2);
      assertThat(registry.count(map("connectionID", "c2"))).isEqualTo(1);
      assertThat(registry.deleteAll()).isEqualTo(1);
      assertThat(Files.readAllLines(file).get(0))
          .isEqualTo("{\"$$indexCreated\":{\"fieldName\":\"expirationTime\",\"expireAfterSeconds\":172800}}");
    } finally {
      registry.datastore().close();
    }
  }

  @Test
  void theTtlIndexOnlyExpiresDatesAndOnlyInTheProcessThatCreatedIt() {
    Path file = project.resolve(".booster/subscriptions.json");
    WebSocketRegistry registry = new WebSocketRegistry(file);
    registry.store(map("connectionID", "number", "expirationTime", 1));
    registry.store(map("connectionID", "date", "expirationTime", new NedbDate(0)));
    assertThat(registry.query(map())).extracting(d -> d.get("connectionID")).containsExactly("number");
    registry.store(map("connectionID", "date2", "expirationTime", new NedbDate(0)));
    registry.datastore().close();
    // after a reload NeDB restores the index without its TTL, so nothing expires any more
    WebSocketRegistry reloaded = new WebSocketRegistry(file);
    try {
      assertThat(reloaded.query(map())).extracting(d -> d.get("connectionID")).containsExactly("number", "date2");
    } finally {
      reloaded.datastore().close();
    }
  }

  @Test
  void journalLifecycle() {
    DispatchJournal journal = new DispatchJournal(project.resolve(".booster/dispatch_journal.json"));
    try {
      journal.addPending(List.of(map("_id", "e1", "entityTypeName", "Cart", "entityID", "c1"),
          map("_id", "e2", "entityTypeName", "Cart", "entityID", "c1")));
      assertThat(journal.size()).isEqualTo(2);
      assertThat(journal.isInFlight("e1")).isTrue();
      // dispatches of this process are not reported as pending
      assertThat(journal.pendingEntries()).isEmpty();
      journal.untrack(List.of("e1", "e2"));
      assertThat(journal.pendingEntries()).extracting(e -> e.get("_id")).containsExactly("e1", "e2");
      assertThat(journal.markDispatched("e1")).isTrue();
      assertThat(journal.markDispatched("e1")).isFalse();
      assertThat(journal.markDispatched("unknown")).isTrue();
      assertThat(journal.interruptedEntries()).extracting(e -> e.get("_id")).containsExactly("e1");
      journal.removeDispatched(List.of("e1", "e2"));
      assertThat(journal.pendingEntries()).extracting(e -> e.get("_id")).containsExactly("e2");
      journal.remove(List.of("e2"));
      assertThat(journal.size()).isZero();
      journal.addPending(List.of());
      journal.remove(List.of());
    } finally {
      journal.datastore().close();
    }
  }

  @Test
  void theJournalCompactsItselfAsItGrows() throws Exception {
    Path file = project.resolve(".booster/dispatch_journal.json");
    DispatchJournal journal = new DispatchJournal(file);
    try {
      for (int i = 0; i < 600; i++) {
        journal.addPending(List.of(map("_id", "e" + i, "entityTypeName", "Cart", "entityID", "c")));
        journal.markDispatched("e" + i);
        journal.remove(List.of("e" + i));
      }
      assertThat(Files.readAllLines(file).size()).isLessThan(DispatchJournal.AUTOCOMPACTION_LINE_THRESHOLD + 10);
    } finally {
      journal.datastore().close();
    }
  }

  @Test
  void theJournalFileFollowsItsMultiEventBatchesThroughGrowthCompactions() throws Exception {
    // the lifecycle of storeEvents with two-event batches: the removal that crosses the threshold compacts the file,
    // which must not bring back the entries it removes (they would be reported as interrupted after a restart)
    Path file = project.resolve(".booster/dispatch_journal.json");
    DispatchJournal journal = new DispatchJournal(file);
    try {
      for (int batch = 0; batch < 400; batch++) {
        List<String> ids = List.of("b" + batch + "e0", "b" + batch + "e1");
        journal.addPending(ids.stream().map(id -> map("_id", id, "entityTypeName", "Cart", "entityID", "c")).toList());
        ids.forEach(journal::markDispatched);
        journal.remove(ids);
        // a copy of the file is what a crash at this point leaves (loading the file itself would compact it)
        Path copy = project.resolve("crash-copy.json");
        Files.copy(file, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        DispatchJournal reread = new DispatchJournal(copy);
        try {
          assertThat(reread.size()).as("journal file after batch %d", batch).isZero();
        } finally {
          reread.datastore().close();
        }
        assertThat(journal.size()).isZero();
      }
    } finally {
      journal.datastore().close();
    }
  }

  @Test
  void aDispatchThatFinishesWhileTheJournalIsReadIsNotTakenForAnUndispatchedOne() {
    HookedDatastore store = new HookedDatastore(DatastoreOptions.of(project.resolve(".booster/dispatch_journal.json")));
    DispatchJournal journal = new DispatchJournal(store);
    try {
      // a dispatch of this process is running: its entry is pending
      journal.addPending(List.of(map("_id", "live", "entityTypeName", "Cart", "entityID", "c1")));
      // an entry a crash left behind
      store.insert(map("_id", "leftover", "entityTypeName", "Cart", "entityID", "c2", "state",
          DispatchJournal.PENDING));
      // the live dispatch completes right after the recovery read the pending entries, before it checks them
      store.afterNextFind(query -> DispatchJournal.PENDING.equals(query.get("state")), () -> {
        assertThat(journal.markDispatched("live")).isTrue();
        journal.remove(List.of("live"));
      });
      assertThat(journal.pendingEntries()).extracting(entry -> entry.get("_id")).containsExactly("leftover");

      journal.addPending(List.of(map("_id", "live2", "entityTypeName", "Cart", "entityID", "c1")));
      journal.markDispatched("live2");
      store.afterNextFind(query -> DispatchJournal.DISPATCHED.equals(query.get("state")),
          () -> journal.remove(List.of("live2")));
      assertThat(journal.interruptedEntries()).isEmpty();
    } finally {
      store.close();
    }
  }
}
