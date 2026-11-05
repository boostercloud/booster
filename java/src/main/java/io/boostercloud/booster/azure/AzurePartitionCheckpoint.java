package io.boostercloud.booster.azure;

import io.boostercloud.booster.core.*;
import java.time.*;
import java.util.*;

/** Renewable partition ownership and progress in one CAS document; failed work never advances progress. */
final class AzurePartitionCheckpoint {
  private static final String NAMESPACE = "azure.eventhub-checkpoints";
  private final StateStore state;
  private final String owner;
  private final Clock clock;
  AzurePartitionCheckpoint(StateStore state, String owner, Clock clock) { this.state = state; this.owner = owner; this.clock = clock; }
  OptionalLong acquire(String partition) {
    var existing = state.get(NAMESPACE, partition);
    var value = existing.map(StateDocument::value).orElse(Json.MAPPER.createObjectNode());
    long now = clock.millis();
    if (value.path("until").asLong() > now && !owner.equals(value.path("owner").asText())) return OptionalLong.empty();
    long sequence = value.path("sequence").asLong(-1);
    var next = Json.MAPPER.createObjectNode().put("owner", owner).put("until", now + 30000).put("sequence", sequence);
    try { state.put(NAMESPACE, partition, existing.map(StateDocument::version).orElse(0L), next); return OptionalLong.of(sequence); }
    catch (OptimisticConcurrencyException race) { return OptionalLong.empty(); }
  }
  void advance(String partition, long sequence) {
    if (sequence < 0) throw new IllegalArgumentException("Negative Event Hubs sequence");
    var current = state.get(NAMESPACE, partition).orElseThrow();
    var value = current.value();
    if (!owner.equals(value.path("owner").asText()) || value.path("until").asLong() <= clock.millis())
      throw new IllegalStateException("Event Hubs consumer lost its partition lease");
    var next = Json.MAPPER.createObjectNode().put("owner", owner).put("until", clock.millis() + 30000)
        .put("sequence", Math.max(sequence, value.path("sequence").asLong(-1)));
    state.put(NAMESPACE, partition, current.version(), next);
  }
}
