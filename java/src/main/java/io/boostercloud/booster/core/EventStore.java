package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An atomic durable journal. Implementations must serialize commits, validate every expected
 * stream version before writing, and atomically persist the request receipt with its events.
 * Reusing a request ID with another fingerprint must throw a CONFLICT BoosterException.
 * Positions are committed, consecutive global journal positions starting at one.
 */
public interface EventStore extends AutoCloseable {
  Optional<CommandReceipt> findCommand(String requestId);
  CommitResult commit(String requestId, String fingerprint, Map<StreamId, Long> expectedVersions,
                      List<PendingEvent> events, JsonNode result);
  /** Atomically fence whole-journal validation (including joins and history administration). */
  default CommitResult commitAtPosition(String requestId, String fingerprint, Map<StreamId, Long> expectedVersions,
                                       List<PendingEvent> events, JsonNode result, long expectedPosition) {
    throw new UnsupportedOperationException("This event store does not support journal-position preconditions");
  }
  List<StoredEvent> readStream(StreamId stream, long afterVersion);
  List<StoredEvent> readAll(long afterPosition, int limit);
  long lastPosition();
  /** Clear a soft-deleted event payload and receipt event copies after its durable deletion marker exists. */
  default boolean redactEventPayload(String eventId, java.time.Instant deletedAt) {
    throw new UnsupportedOperationException("This event store does not support event administration");
  }
  @Override default void close() {}
}
