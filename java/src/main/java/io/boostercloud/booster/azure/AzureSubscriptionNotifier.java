package io.boostercloud.booster.azure;

import com.azure.cosmos.*;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable Cosmos projection change feed. Lease checkpoint advances only after every send succeeds. */
public final class AzureSubscriptionNotifier implements AutoCloseable {
  private final ChangeFeedProcessor processor;
  private final AzureWebPubSubHandler handler;
  private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("booster-azure-subscriptions").factory());
  private final AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean();
  AzureSubscriptionNotifier(CosmosAsyncContainer state, CosmosAsyncContainer leases, String application,
      String workerName, AzureWebPubSubHandler handler) {
    this.handler = handler;
    processor = new ChangeFeedProcessorBuilder().hostName(workerName + ":" + UUID.randomUUID()).feedContainer(state).leaseContainer(leases)
        .options(new ChangeFeedProcessorOptions().setLeasePrefix("booster-notify-" + CosmosEventStore.hash(application))
            .setStartFromBeginning(true).setMaxItemCount(100).setFeedPollDelay(Duration.ofSeconds(1)))
        .handleChanges(documents -> {
          for (var document : documents) {
            if (!application.equals(document.path("pk").asText()) || !"state".equals(document.path("kind").asText())) continue;
            toChange(document).ifPresent(handler::notifyChange);
          }
        }).buildChangeFeedProcessor();
  }
  static Optional<ReadModelChange> toChange(JsonNode document) {
    String namespace = document.path("namespace").asText();
    if (!namespace.startsWith("readModels:")) return Optional.empty();
    var state = CosmosEventStore.decode(document.get("state"), StateDocument.class);
    var envelope = state.value();
    if (!envelope.has("state") || !envelope.path("position").canConvertToLong()) throw new IllegalArgumentException("Invalid persisted projection envelope");
    return Optional.of(new ReadModelChange(namespace.substring("readModels:".length()), envelope.path("id").asText(state.key()),
        envelope.get("state"), envelope.get("position").asLong(), envelope.hasNonNull("sequenceKey") ? envelope.path("sequenceKey").asText() : null,
        envelope.get("sequenceValue")));
  }
  public void start() {
    if (closed.get()) throw new IllegalStateException("Azure subscription notifier is closed");
    if (!started.compareAndSet(false, true)) return;
    processor.start().block(Duration.ofSeconds(60));
    cleanup.scheduleWithFixedDelay(() -> {
      try { handler.closeExpiredConnections(); cleanupFailure.set(null); }
      catch (RuntimeException failure) { cleanupFailure.set(failure); }
    }, 0, 30, TimeUnit.SECONDS);
  }
  public Optional<Throwable> cleanupFailure() { return Optional.ofNullable(cleanupFailure.get()); }
  @Override public void close() {
    if (!closed.compareAndSet(false, true)) return;
    cleanup.shutdownNow();
    if (started.get()) processor.stop().block(Duration.ofSeconds(30));
  }
}
