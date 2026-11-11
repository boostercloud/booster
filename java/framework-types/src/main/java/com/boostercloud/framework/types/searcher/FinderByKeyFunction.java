package com.boostercloud.framework.types.searcher;

import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The by-id backend of a {@link Searcher}. Port of {@code FinderByKeyFunction}.
 *
 * @param <T> the searched type
 */
@FunctionalInterface
public interface FinderByKeyFunction<T> {
  /**
   * @param objectClass the searched class
   * @param id the id
   * @param sequenceKey the sequence key, or {@code null}
   * @return the instance, or a {@code List<T>} for sequenced read models queried without sequence key
   */
  Object find(Class<T> objectClass, UUID id, SequenceKey sequenceKey);
}
