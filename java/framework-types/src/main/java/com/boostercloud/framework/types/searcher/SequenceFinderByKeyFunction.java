package com.boostercloud.framework.types.searcher;

import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * Port of {@code SequenceFinderByKeyFunction}.
 *
 * @param <T> the searched type
 */
@FunctionalInterface
public interface SequenceFinderByKeyFunction<T> {
  /**
   * @param className the searched class name
   * @param id the id
   * @param sequenceKey the sequence key, or {@code null}
   * @return the instance
   */
  T find(String className, UUID id, SequenceKey sequenceKey);
}
