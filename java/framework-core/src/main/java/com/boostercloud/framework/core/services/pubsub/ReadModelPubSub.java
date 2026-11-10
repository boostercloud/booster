package com.boostercloud.framework.core.services.pubsub;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import java.util.Iterator;

/**
 * Port of {@code ReadModelPubSub} ({@code services/pub-sub/read-model-pub-sub.ts}): the source of the read models a
 * GraphQL subscription resolver yields. TypeScript returns an {@code AsyncIterator}; the blocking Java counterpart is
 * an {@link Iterator} (stage core-3 adapts it to graphql-java's publisher).
 *
 * <p>Owned by stage <b>core-2b</b> (spec 07 §3.6), which may refine the iteration type together with core-3.
 */
public interface ReadModelPubSub {
  /**
   * {@code asyncIterator(readModelRequestEnvelope, config)}.
   *
   * @param readModelRequestEnvelope the subscription request
   * @param config the configuration
   * @return the matching read models
   */
  Iterator<Object> asyncIterator(ReadModelRequestEnvelope readModelRequestEnvelope, BoosterConfig config);
}
