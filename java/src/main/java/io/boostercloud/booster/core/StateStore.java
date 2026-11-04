package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.List;
import java.util.Optional;

/** Provider compare-and-set state used for projections, snapshots and resumable jobs. */
public interface StateStore extends AutoCloseable {
  Optional<StateDocument> get(String namespace, String key);
  /** Lexicographic key pagination, including tombstones. An absent cursor starts the namespace. */
  List<StateDocument> scan(String namespace, String afterKey, int limit);
  /** Version zero requires absence; otherwise exactly the stored version must match. */
  StateDocument put(String namespace, String key, long expectedVersion, JsonNode value);
  /** Retain the revision to prevent delete/recreate ABA races. */
  default void delete(String namespace, String key, long expectedVersion) {
    put(namespace, key, expectedVersion, NullNode.instance);
  }
  @Override default void close() {}
}
