package io.boostercloud.booster.azure;

import com.azure.cosmos.*;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.azure.messaging.eventhubs.*;
import com.azure.messaging.eventhubs.models.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cosmos leased change feed -> Event Hubs -> journal replay/handler recovery.
 * Publishing is at least once. Consumers checkpoint only after successful replay
 * and handler completion; core handler receipts provide durable deduplication.
 */
public final class AzureEventPipeline implements AutoCloseable {
  private final ChangeFeedProcessor changeFeed;
  private final EventHubProducerClient producer;
  private final EventHubConsumerClient consumer;
  private final AzurePartitionCheckpoint checkpoints;
  private final Booster runtime;
  private final String application, owner;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean();
  private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
  AzureEventPipeline(CosmosAsyncContainer feed, CosmosAsyncContainer leases, EventHubProducerClient producer,
      EventHubConsumerClient consumer, StateStore state, Booster runtime, String application, String workerName) {
    this.producer = Objects.requireNonNull(producer); this.consumer = Objects.requireNonNull(consumer);
    this.runtime = Objects.requireNonNull(runtime);
    this.application = application;
    if (workerName == null || workerName.isBlank()) throw new IllegalArgumentException("Worker name required");
    this.owner = workerName + ":" + UUID.randomUUID();
    this.checkpoints = new AzurePartitionCheckpoint(Objects.requireNonNull(state), owner, Clock.systemUTC());
    changeFeed = new ChangeFeedProcessorBuilder().hostName(owner).feedContainer(feed).leaseContainer(leases)
        .options(new ChangeFeedProcessorOptions().setLeasePrefix("booster-" + CosmosEventStore.hash(application))
            .setStartFromBeginning(true).setMaxItemCount(100).setFeedPollDelay(Duration.ofSeconds(1)))
        .handleChanges(this::publishChanges).buildChangeFeedProcessor();
  }
  public void start() {
    if (closed.get()) throw new IllegalStateException("Azure pipeline is closed");
    if (!started.compareAndSet(false, true)) return;
    try {
      changeFeed.start().block(Duration.ofSeconds(60));
      for (String partition : consumer.getPartitionIds()) workers.submit(() -> consume(partition));
    } catch (RuntimeException failure) { lastFailure.set(failure); close(); throw failure; }
  }
  void publishChanges(List<JsonNode> documents) {
    // A transaction changes stream heads/receipts as well; publish immutable events only.
    var groups = new LinkedHashMap<String, List<StoredEvent>>();
    for (var document : documents) {
      if (!"event".equals(document.path("kind").asText()) || !application.equals(document.path("pk").asText())) continue;
      var event = CosmosEventStore.decode(document.get("event"), StoredEvent.class);
      groups.computeIfAbsent(CosmosEventStore.streamId(event.event().stream()), ignored -> new ArrayList<>()).add(event);
    }
    for (var entry : groups.entrySet()) {
      var options = new CreateBatchOptions().setPartitionKey(entry.getKey());
      var batch = producer.createBatch(options);
      for (var event : entry.getValue()) {
        var message = new EventData(Json.MAPPER.valueToTree(event).toString());
        message.getProperties().put("booster.application", application);
        message.getProperties().put("booster.eventId", event.event().id());
        if (!batch.tryAdd(message)) {
          if (batch.getCount() == 0) throw new IllegalArgumentException("Event exceeds Event Hubs message size");
          producer.send(batch); batch = producer.createBatch(options);
          if (!batch.tryAdd(message)) throw new IllegalArgumentException("Event exceeds Event Hubs message size");
        }
      }
      if (batch.getCount() > 0) producer.send(batch);
    }
  }
  private void consume(String partition) {
    while (!closed.get()) {
      try {
        var lease = checkpoints.acquire(partition);
        if (lease.isEmpty()) { pause(); continue; }
        long previousSequence = lease.getAsLong();
        var position = previousSequence < 0 ? EventPosition.earliest() : EventPosition.fromSequenceNumber(previousSequence, false);
        for (var message : consumer.receiveFromPartition(partition, 100, position, Duration.ofSeconds(2))) {
          if (closed.get()) return;
          var data = message.getData();
          if (application.equals(data.getProperties().get("booster.application"))) {
            var event = Json.MAPPER.readValue(data.getBodyAsString(), StoredEvent.class);
            // Never mutate the journal from an Event Hubs payload. Replay the authoritative
            // journal, including all earlier events, then retry unfinished durable handlers.
            if (runtime.position() < event.position()) throw new IllegalStateException("Event Hubs notification precedes visible Cosmos journal");
            runtime.resumeHandlers();
          }
          checkpoints.advance(partition, data.getSequenceNumber());
        }
        lastFailure.set(null);
      } catch (Exception failure) {
        lastFailure.set(failure);
        if (!closed.get()) pause();
      }
    }
  }
  private void pause() {
    try { Thread.sleep(1000); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
  }
  public Optional<Throwable> lastFailure() { return Optional.ofNullable(lastFailure.get()); }
  @Override public void close() {
    if (!closed.compareAndSet(false, true)) return;
    workers.shutdownNow();
    if (started.get()) changeFeed.stop().block(Duration.ofSeconds(30));
    try {
      if (!workers.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Azure event workers did not stop within 30 seconds");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while stopping Azure event workers", interrupted);
    }
  }
}
