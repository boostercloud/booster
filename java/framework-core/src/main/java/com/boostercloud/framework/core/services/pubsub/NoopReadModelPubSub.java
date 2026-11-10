package com.boostercloud.framework.core.services.pubsub;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import java.util.Iterator;

/**
 * Port of {@code NoopReadModelPubSub}: always yields nothing (used for normal client requests, when subscriptions are
 * registered rather than notified).
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 07 §3.6.4).
 */
public class NoopReadModelPubSub implements ReadModelPubSub {

  /** Creates the pub-sub. */
  public NoopReadModelPubSub() {}

  @Override
  public Iterator<Object> asyncIterator(ReadModelRequestEnvelope readModelRequestEnvelope, BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: NoopReadModelPubSub#asyncIterator");
  }
}
