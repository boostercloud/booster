package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * The provider under concurrent load from virtual threads, the way core uses it while many requests run at once:
 * optimistic-concurrency read model updates on hot read models, event batches with in-process dispatch and the
 * dispatch journal, and websocket bookkeeping. Every result is checked again after reopening the files.
 */
class ProviderStressTest {

  @TempDir
  Path project;

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

  private LocalProvider open(FakeUserApp app) {
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(false, "0").withUserApp(() -> app));
    runtimes.add(runtime);
    LocalProvider provider = new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage);
    app.provider(provider);
    return provider;
  }

  @Test
  void hotReadModelsUpdatedConcurrentlyLoseNoIncrement() throws Exception {
    FakeUserApp app = new FakeUserApp(config);
    LocalProvider provider = open(app);
    int readModels = 4;
    int threads = 24;
    int incrementsPerThread = 6;
    for (int r = 0; r < readModels; r++) {
      provider.readModels().store(config, "Counter", map("id", "rm-" + r, "count", 0,
          "boosterMetadata", map("version", 1, "schemaVersion", 1)), 0L);
    }
    AtomicInteger conflicts = new AtomicInteger();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> tasks = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        String id = "rm-" + (t % readModels);
        tasks.add(executor.submit(() -> {
          for (int i = 0; i < incrementsPerThread; i++) {
            while (true) {
              Map<String, Object> current = provider.readModels().fetch(config, "Counter", UUID.of(id), null).getFirst();
              long version = ((Number) ((Map<?, ?>) current.get("boosterMetadata")).get("version")).longValue();
              Map<String, Object> next = map("id", id, "count", ((Number) current.get("count")).intValue() + 1,
                  "boosterMetadata", map("version", version + 1, "schemaVersion", 1));
              try {
                provider.readModels().store(config, "Counter", next, version);
                break;
              } catch (OptimisticConcurrencyUnexpectedVersionError e) {
                conflicts.incrementAndGet();
              }
            }
          }
          return null;
        }));
      }
      for (Future<?> task : tasks) {
        task.get();
      }
    }
    int perReadModel = threads / readModels * incrementsPerThread;
    assertThat(conflicts.get()).isPositive();
    for (java.util.function.Supplier<LocalProvider> next : List.<java.util.function.Supplier<LocalProvider>>of(
        () -> provider, this::reopen)) {
      LocalProvider view = next.get();
      for (int r = 0; r < readModels; r++) {
        Map<String, Object> stored = view.readModels().fetch(config, "Counter", UUID.of("rm-" + r), null).getFirst();
        assertThat(stored.get("count")).isEqualTo(perReadModel);
        assertThat(((Number) ((Map<?, ?>) stored.get("boosterMetadata")).get("version")).intValue())
            .isEqualTo(1 + perReadModel);
      }
      // a single document per read model: no lost or duplicated versions
      assertThat(view.readModels().search(config, "Counter", map(), null, null, null, false, null).items())
          .hasSize(readModels);
    }
    // every successful update notified the subscribers once
    assertThat(app.notifications).hasSize(readModels + readModels * perReadModel);
  }

  private LocalProvider reopen() {
    runtimes.forEach(LocalProviderRuntime::close);
    return open(new FakeUserApp(config));
  }

  @Test
  void concurrentEventBatchesAreAllStoredDispatchedOnceAndJournaledAway() throws Exception {
    FakeUserApp app = new FakeUserApp(config);
    LocalProvider provider = open(app);
    int threads = 24;
    int batchesPerThread = 4;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> tasks = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        int thread = t;
        tasks.add(executor.submit(() -> {
          for (int b = 0; b < batchesPerThread; b++) {
            // half of the threads share one hot entity, the others have their own
            String entity = thread % 2 == 0 ? "hot" : "cart-" + thread;
            provider.events().store(List.of(event(entity, thread, b, 0), event(entity, thread, b, 1)), config);
          }
          return null;
        }));
      }
      for (Future<?> task : tasks) {
        task.get();
      }
    }
    int total = threads * batchesPerThread * 2;
    assertThat(app.dispatchedBatches).hasSize(threads * batchesPerThread);
    assertThat(app.processedEvents).hasSize(total).extracting(EventEnvelope::id).doesNotHaveDuplicates();
    assertThat(app.skippedEvents).isEmpty();
    for (java.util.function.Supplier<LocalProvider> next : List.<java.util.function.Supplier<LocalProvider>>of(
        () -> provider, this::reopen)) {
      LocalProvider view = next.get();
      List<EventEnvelope> hot = view.events().forEntitySince(config, "Cart", UUID.of("hot"), null);
      assertThat(hot).hasSize(threads / 2 * batchesPerThread * 2);
      assertThat(hot).isSortedAccordingTo((a, b) -> a.createdAt().compareTo(b.createdAt()));
      // the two events of a batch stay in order (same millisecond or not)
      Map<String, Integer> lastSequence = new java.util.HashMap<>();
      for (EventEnvelope event : hot) {
        Map<?, ?> value = (Map<?, ?>) event.value();
        String batch = value.get("thread") + "/" + value.get("batch");
        int sequence = ((Number) value.get("sequence")).intValue();
        assertThat(sequence).isGreaterThan(lastSequence.getOrDefault(batch, -1));
        lastSequence.put(batch, sequence);
      }
      Set<Object> ids = new HashSet<>();
      PaginatedEntitiesIdsResult page = view.events().searchEntitiesIDs(config, 1000, null, "Cart");
      page.items().forEach(item -> ids.add(item.entityID()));
      assertThat(ids).hasSize(1 + threads / 2);
      assertThat(view.runtime().dispatchJournal().size()).isZero();
      assertThat(view.recoverUndispatchedEvents(config)).isEmpty();
    }
  }

  private static NonPersistedEventEnvelope event(String entity, int thread, int batch, int sequence) {
    return NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of(entity)).requestID(UUID.generate())
        .entityTypeName("Cart").typeName("ItemAdded")
        .value(map("thread", thread, "batch", batch, "sequence", sequence)).build();
  }

  @Test
  void concurrentConnectionsAndSubscriptionsKeepTheirBookkeeping() throws Exception {
    LocalProvider provider = open(new FakeUserApp(config));
    int connections = 40;
    ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().requestID(UUID.generate())
        .className("CartReadModel").version(1).filters(map()).build();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> tasks = new ArrayList<>();
      for (int c = 0; c < connections; c++) {
        String connection = "conn-" + c;
        tasks.add(executor.submit(() -> {
          provider.connections().storeData(config, connection, new ConnectionDataEnvelope(1_900_000_000, null));
          for (int s = 0; s < 3; s++) {
            provider.readModels().subscribe(config, SubscriptionEnvelope.of(request, 1_900_000_000, connection,
                GraphQLOperation.builder().id("op-" + s).query("subscription { x }").build()));
          }
          // stop one subscription; even connections disconnect
          provider.readModels().deleteSubscription(config, connection, "op-1");
          if (connection.hashCode() % 2 == 0) {
            provider.readModels().deleteAllSubscriptions(config, connection);
            provider.connections().deleteData(config, connection);
          }
          return null;
        }));
      }
      for (Future<?> task : tasks) {
        task.get();
      }
    }
    long disconnected = java.util.stream.IntStream.range(0, connections)
        .filter(c -> ("conn-" + c).hashCode() % 2 == 0).count();
    for (java.util.function.Supplier<LocalProvider> next : List.<java.util.function.Supplier<LocalProvider>>of(
        () -> provider, this::reopen)) {
      LocalProvider view = next.get();
      List<SubscriptionEnvelope> subscriptions = view.readModels().fetchSubscriptions(config, "CartReadModel");
      assertThat(subscriptions).hasSize((int) (connections - disconnected) * 2);
      assertThat(subscriptions).extracting(s -> s.operation().id()).doesNotContain("op-1");
      for (int c = 0; c < connections; c++) {
        boolean connected = ("conn-" + c).hashCode() % 2 != 0;
        assertThat(view.connections().fetchData(config, "conn-" + c).isPresent()).isEqualTo(connected);
      }
    }
  }
}
