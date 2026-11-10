package com.boostercloud.framework.core.services.pubsub;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import java.util.Collections;
import java.util.Iterator;

/**
 * Port of {@code NoopReadModelPubSub}: always yields nothing (used for normal client requests, when subscriptions are
 * registered rather than notified). The envelope and the configuration are ignored.
 */
public class NoopReadModelPubSub implements ReadModelPubSub {

  /** Creates the pub-sub. */
  public NoopReadModelPubSub() {}

  @Override
  public Iterator<Object> asyncIterator(ReadModelRequestEnvelope readModelRequestEnvelope, BoosterConfig config) {
    return Collections.emptyIterator();
  }

  /** Every no-op pub-sub is equal to another (TypeScript tests compare them with deep equality). */
  @Override
  public boolean equals(Object other) {
    return other instanceof NoopReadModelPubSub;
  }

  @Override
  public int hashCode() {
    return NoopReadModelPubSub.class.hashCode();
  }

  @Override
  public String toString() {
    return "NoopReadModelPubSub";
  }
}
