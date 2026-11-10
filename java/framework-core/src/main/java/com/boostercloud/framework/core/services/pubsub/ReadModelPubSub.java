package com.boostercloud.framework.core.services.pubsub;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import java.util.Iterator;
import java.util.concurrent.Flow;

/**
 * Port of {@code ReadModelPubSub} ({@code services/pub-sub/read-model-pub-sub.ts}): the source of the read models a
 * GraphQL subscription resolver yields. TypeScript returns an {@code AsyncIterator}; the blocking Java counterpart is
 * a finite {@link Iterator} (its elements are produced before it is returned). A subscription data fetcher hands it to
 * graphql-java as a publisher with {@link #toPublisher(Iterator)}.
 *
 * <p>Implementations: {@link NoopReadModelPubSub} (client requests: registering a subscription yields nothing) and
 * {@link FilteredReadModelPubSub} (notifications: the changed read models that match the subscription).
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

  /**
   * The {@link Flow.Publisher} of the elements of an iterator (graphql-java accepts it as the result of a subscription
   * data fetcher): one subscriber, demand honoured, completion when the iterator is exhausted.
   *
   * @param iterator the iterator returned by {@link #asyncIterator}
   * @return the publisher
   */
  static Flow.Publisher<Object> toPublisher(Iterator<?> iterator) {
    return new IteratorPublisher(iterator);
  }
}
