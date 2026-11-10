package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.provider.local.services.DispatchJournal;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Java-only crash recovery: events stored but not dispatched when the process stopped are re-dispatched by
 * {@code recoverUndispatchedEvents}; core's {@code storeDispatched} dedup makes a repeated dispatch harmless.
 */
class RecoveryTest {

  @TempDir
  Path directory;

  private final List<LocalProviderRuntime> runtimes = new ArrayList<>();
  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
  }

  @AfterEach
  void tearDown() {
    runtimes.forEach(LocalProviderRuntime::close);
  }

  private LocalProvider provider(Path project, FakeUserApp app) {
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(false, "0").withUserApp(() -> app));
    runtimes.add(runtime);
    LocalProvider provider = new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage);
    app.provider(provider);
    return provider;
  }

  static NonPersistedEventEnvelope event(String entityId, int quantity) {
    return NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of(entityId)).requestID(UUID.generate())
        .entityTypeName("Cart").typeName("CartItemChanged").value(map("cartId", entityId, "quantity", quantity))
        .build();
  }

  /** A copy of the data files as they are on disk right now: what a crash at this moment would leave. */
  private Path crashCopy(Path project) throws IOException {
    Path copy = Files.createDirectories(directory.resolve("crash-" + System.nanoTime()).resolve(".booster"));
    try (Stream<Path> files = Files.list(project.resolve(".booster"))) {
      for (Path file : files.toList()) {
        Files.copy(file, copy.resolve(file.getFileName().toString()));
      }
    }
    return copy.getParent();
  }

  /** An app whose dispatcher blocks before or after core marks the events as dispatched. */
  private static final class BlockingApp extends FakeUserApp {
    final CountDownLatch reached = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    private final boolean afterMarking;

    BlockingApp(BoosterConfig config, boolean afterMarking) {
      super(config);
      this.afterMarking = afterMarking;
      if (afterMarking) {
        onEvent(event -> block());
      }
    }

    private void block() {
      reached.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void boosterEventDispatcher(Object rawEvents) {
      if (!afterMarking) {
        block();
      }
      super.boosterEventDispatcher(rawEvents);
    }
  }

  @Test
  void eventsStoredBeforeACrashAreDispatchedOnRecovery() throws Exception {
    Path project = directory.resolve("app");
    BlockingApp app = new BlockingApp(config, false);
    LocalProvider provider = provider(project, app);
    CompletableFuture<List<EventEnvelope>> storing = CompletableFuture.supplyAsync(
        () -> provider.events().store(List.of(event("cart-1", 1), event("cart-1", 2), event("cart-2", 3)), config),
        command -> Thread.ofVirtual().start(command));
    assertThat(app.reached.await(10, TimeUnit.SECONDS)).isTrue();
    Path crashed = crashCopy(project);
    app.release.countDown();
    List<EventEnvelope> stored = storing.get(10, TimeUnit.SECONDS);
    assertThat(provider.runtime().dispatchJournal().size()).isZero();
    assertThat(provider.recoverUndispatchedEvents(config)).isEmpty();

    FakeUserApp restarted = new FakeUserApp(config);
    LocalProvider recovered = provider(crashed, restarted);
    List<EventEnvelope> redispatched = recovered.recoverUndispatchedEvents(config);
    assertThat(redispatched).extracting(EventEnvelope::id).containsExactlyElementsOf(
        stored.stream().map(EventEnvelope::id).toList());
    assertThat(redispatched).extracting(EventEnvelope::createdAt)
        .containsExactlyElementsOf(stored.stream().map(EventEnvelope::createdAt).toList());
    assertThat(restarted.processedEvents).hasSize(3);
    assertThat(restarted.dispatchedBatches).hasSize(1);
    assertThat(recovered.runtime().dispatchJournal().size()).isZero();
    // a second recovery has nothing left to do, and the events can still be read normally
    assertThat(recovered.recoverUndispatchedEvents(config)).isEmpty();
    assertThat(recovered.events().forEntitySince(config, "Cart", UUID.of("cart-1"))).hasSize(2);
  }

  @Test
  void eventsWhoseProcessingStartedAreNotDispatchedAgain() throws Exception {
    Path project = directory.resolve("app");
    BlockingApp app = new BlockingApp(config, true);
    LocalProvider provider = provider(project, app);
    CompletableFuture<List<EventEnvelope>> storing = CompletableFuture.supplyAsync(
        () -> provider.events().store(List.of(event("cart-1", 1)), config), command -> Thread.ofVirtual().start(command));
    assertThat(app.reached.await(10, TimeUnit.SECONDS)).isTrue();
    Path crashed = crashCopy(project);
    app.release.countDown();
    storing.get(10, TimeUnit.SECONDS);

    FakeUserApp restarted = new FakeUserApp(config);
    LocalProvider recovered = provider(crashed, restarted);
    assertThat(recovered.recoverUndispatchedEvents(config)).isEmpty();
    assertThat(restarted.dispatchedBatches).isEmpty();
    assertThat(recovered.runtime().dispatchJournal().size()).isZero();
    Mockito.verify(config.logger).warn(Mockito.eq("[Booster]|events-adapter#recoverUndispatchedEvents: "),
        Mockito.startsWith("The processing of 1 event(s) was interrupted"), Mockito.any());
  }

  @Test
  void redispatchingIsDedupedByStoreDispatched() {
    Path project = directory.resolve("app");
    FakeUserApp app = new FakeUserApp(config);
    LocalProvider provider = provider(project, app);
    List<EventEnvelope> stored = provider.events().store(List.of(event("cart-1", 1)), config);
    assertThat(app.processedEvents).hasSize(1);
    // an event without a journal entry is always processed (TypeScript's storeDispatched)
    assertThat(provider.events().storeDispatched(stored.get(0), config)).isTrue();
    // while it is in the journal, only the first storeDispatched wins
    DispatchJournal journal = provider.runtime().dispatchJournal();
    journal.addPending(List.of(map("_id", stored.get(0).id(), "entityTypeName", "Cart", "entityID", "cart-1")));
    assertThat(provider.events().storeDispatched(stored.get(0), config)).isTrue();
    assertThat(provider.events().storeDispatched(stored.get(0), config)).isFalse();
    assertThat(provider.events().storeDispatched(stored.get(0).toBuilder().id(null).build(), config)).isTrue();
  }

  @Test
  void journalEntriesOfEventsThatWereNeverStoredAreDropped() throws IOException {
    Path project = directory.resolve("app");
    Files.createDirectories(project.resolve(".booster"));
    Files.writeString(project.resolve(".booster/dispatch_journal.json"),
        "{\"_id\":\"neverStoredEvent\",\"entityTypeName\":\"Cart\",\"entityID\":\"cart-1\",\"state\":\"pending\","
            + "\"storedAt\":\"2026-11-10T09:00:00.000Z\"}\n");
    FakeUserApp app = new FakeUserApp(config);
    LocalProvider provider = provider(project, app);
    assertThat(provider.recoverUndispatchedEvents(config)).isEmpty();
    assertThat(app.dispatchedBatches).isEmpty();
    assertThat(provider.runtime().dispatchJournal().size()).isZero();
  }

  @Test
  void typeScriptEventsWithoutJournalEntriesAreNeverRedispatched() throws IOException {
    Path project = directory.resolve("app");
    Files.createDirectories(project.resolve(".booster"));
    Files.writeString(project.resolve(".booster/events.json"),
        "{\"version\":1,\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"c\",\"requestID\":\"r\","
            + "\"entityTypeName\":\"Cart\",\"typeName\":\"E\",\"value\":{},\"createdAt\":\"2026-11-10T09:00:00.000Z\","
            + "\"_id\":\"tsEventId0000000\"}\n");
    FakeUserApp app = new FakeUserApp(config);
    assertThat(provider(project, app).recoverUndispatchedEvents(config)).isEmpty();
    assertThat(app.dispatchedBatches).isEmpty();
  }

  @Test
  void aFailedRecoveryKeepsThePendingEventsForTheNextOne() throws Exception {
    Path project = directory.resolve("app");
    BlockingApp app = new BlockingApp(config, false);
    LocalProvider provider = provider(project, app);
    CompletableFuture<List<EventEnvelope>> storing = CompletableFuture.supplyAsync(
        () -> provider.events().store(List.of(event("cart-1", 1)), config), command -> Thread.ofVirtual().start(command));
    assertThat(app.reached.await(10, TimeUnit.SECONDS)).isTrue();
    Path crashed = crashCopy(project);
    app.release.countDown();
    storing.get(10, TimeUnit.SECONDS);

    AtomicBoolean fail = new AtomicBoolean(true);
    FakeUserApp flaky = new FakeUserApp(config) {
      @Override
      public void boosterEventDispatcher(Object rawEvents) {
        if (fail.getAndSet(false)) {
          throw new IllegalStateException("core is not ready");
        }
        super.boosterEventDispatcher(rawEvents);
      }
    };
    LocalProvider recovered = provider(crashed, flaky);
    assertThatThrownBy(() -> recovered.recoverUndispatchedEvents(config)).hasMessage("core is not ready");
    assertThat(recovered.runtime().dispatchJournal().size()).isEqualTo(1);
    assertThat(recovered.recoverUndispatchedEvents(config)).hasSize(1);
    assertThat(flaky.processedEvents).hasSize(1);
  }

  @Test
  void recoveryNeverTouchesTheDispatchesOfTheRunningProcess() throws Exception {
    Path project = directory.resolve("app");
    BlockingApp app = new BlockingApp(config, false);
    LocalProvider provider = provider(project, app);
    CompletableFuture<List<EventEnvelope>> storing = CompletableFuture.supplyAsync(
        () -> provider.events().store(List.of(event("cart-1", 1)), config), command -> Thread.ofVirtual().start(command));
    assertThat(app.reached.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(provider.recoverUndispatchedEvents(config)).isEmpty();
    app.release.countDown();
    storing.get(10, TimeUnit.SECONDS);
    assertThat(app.processedEvents).hasSize(1);
  }

  @Test
  void failuresWhileStoringOrDispatchingClearTheJournalAsTheCallerRetries() {
    Path project = directory.resolve("app");
    FakeUserApp app = new FakeUserApp(config) {
      @Override
      public void boosterEventDispatcher(Object rawEvents) {
        throw new IllegalStateException("dispatch failed");
      }
    };
    LocalProvider provider = provider(project, app);
    assertThatThrownBy(() -> provider.events().store(List.of(event("cart-1", 1)), config))
        .hasMessage("dispatch failed");
    assertThat(provider.runtime().dispatchJournal().size()).isZero();
    // the event stays stored, as in TypeScript
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("cart-1"))).hasSize(1);

    NonPersistedEventEnvelope invalid = event("cart-2", 1).toBuilder().value(map("bad.key", 1)).build();
    assertThatThrownBy(() -> provider.events().store(List.of(event("cart-2", 1), invalid), config))
        .hasMessage("Field names cannot contain a .");
    assertThat(provider.runtime().dispatchJournal().size()).isZero();
    // the batch is stored atomically: an event that cannot be stored keeps the whole batch out of the store (a crash
    // can therefore never leave part of the events of one command behind)
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("cart-2"))).isEmpty();
  }

  @Test
  void recoversAfterTheProcessIsKilled() throws Exception {
    Path project = directory.resolve("killed");
    String java = ProcessHandle.current().info().command().orElse(
        Path.of(System.getProperty("java.home"), "bin", "java").toString());
    Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
        CrashingApp.class.getName(), project.toString())
        .redirectErrorStream(true)
        .start();
    List<String> acknowledged = new ArrayList<>();
    try (BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(),
        StandardCharsets.UTF_8))) {
      String line;
      while ((line = output.readLine()) != null) {
        if (line.startsWith("STORED ")) {
          acknowledged.add(line.substring("STORED ".length()));
        }
        if (line.equals("DISPATCHING")) {
          break;
        }
      }
      child.destroyForcibly();
      assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(acknowledged).hasSize(CrashingApp.COMMITTED_EVENTS);

    FakeUserApp restarted = new FakeUserApp(config);
    LocalProvider recovered = provider(project, restarted);
    List<EventEnvelope> redispatched = recovered.recoverUndispatchedEvents(config);
    assertThat(redispatched).hasSize(CrashingApp.BATCH_SIZE);
    assertThat(restarted.processedEvents).hasSize(CrashingApp.BATCH_SIZE);
    // every acknowledged write survived the kill
    List<EventEnvelope> all = recovered.events().forEntitySince(config, "Cart", UUID.of("cart-1"));
    assertThat(all.stream().map(EventEnvelope::id).toList()).containsAll(acknowledged);
    assertThat(all).hasSize(CrashingApp.COMMITTED_EVENTS + CrashingApp.BATCH_SIZE);
    await().atMost(Duration.ofSeconds(5)).until(() -> recovered.runtime().dispatchJournal().size() == 0);
  }
}
