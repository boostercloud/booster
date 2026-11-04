package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Thread-safe process-local provider for tests and transient applications. */
public final class InMemoryEventStore implements EventStore {
  private final List<StoredEvent> journal = new ArrayList<>();
  private final Map<StreamId, Long> versions = new HashMap<>();
  private final Map<String, CommandReceipt> receipts = new HashMap<>();
  private final Set<String> eventIds = new HashSet<>();
  private boolean closed;
  private void checkOpen() { if (closed) throw new IllegalStateException("Event store is closed"); }
  @Override public synchronized Optional<CommandReceipt> findCommand(String requestId) {
    checkOpen(); return Optional.ofNullable(receipts.get(requestId));
  }
  @Override public synchronized CommitResult commit(String requestId, String fingerprint,
      Map<StreamId, Long> expectedVersions, List<PendingEvent> events, JsonNode result) {
    checkOpen();
    if (requestId == null || requestId.isBlank() || fingerprint == null || fingerprint.isBlank() || result == null)
      throw new IllegalArgumentException("Request identity, fingerprint and result are required");
    var previous = receipts.get(requestId);
    if (previous != null) {
      if (!previous.fingerprint().equals(fingerprint))
        throw new BoosterException(BoosterException.Code.CONFLICT, "Request ID was already used for another operation");
      return new CommitResult(true, previous);
    }
    expectedVersions.forEach((stream, expected) -> {
      if (expected < 0) throw new IllegalArgumentException("Negative expected version");
      long actual = versions.getOrDefault(stream, 0L);
      if (actual != expected) throw new OptimisticConcurrencyException(stream, expected, actual);
    });
    Set<String> newIds = new HashSet<>();
    for (var event : events) {
      if (!expectedVersions.containsKey(event.stream())) throw new IllegalArgumentException("Missing expected stream version");
      if (eventIds.contains(event.id()) || !newIds.add(event.id()))
        throw new BoosterException(BoosterException.Code.CONFLICT, "Duplicate event ID");
    }
    List<StoredEvent> appended = new ArrayList<>();
    for (var event : events) {
      long version = versions.merge(event.stream(), 1L, Long::sum);
      var stored = new StoredEvent(journal.size() + 1L, version, event);
      journal.add(stored); appended.add(stored); eventIds.add(event.id());
    }
    var receipt = new CommandReceipt(requestId, fingerprint, result, appended);
    receipts.put(requestId, receipt);
    return new CommitResult(false, receipt);
  }
  @Override public synchronized List<StoredEvent> readStream(StreamId stream, long afterVersion) {
    checkOpen(); if (afterVersion < 0) throw new IllegalArgumentException("Negative stream version");
    return journal.stream().filter(e -> e.event().stream().equals(stream) && e.streamVersion() > afterVersion).toList();
  }
  @Override public synchronized List<StoredEvent> readAll(long afterPosition, int limit) {
    checkOpen(); if (afterPosition < 0 || limit < 1) throw new IllegalArgumentException("Invalid journal window");
    return journal.stream().filter(e -> e.position() > afterPosition).limit(limit).toList();
  }
  @Override public synchronized long lastPosition() { checkOpen(); return journal.size(); }
  @Override public synchronized void close() { closed = true; }
}
