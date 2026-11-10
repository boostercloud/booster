package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code booster-event-stream-consumer.ts}: the entry point of events arriving through the event stream
 * (provider-level dedup, then the same pipeline as the dispatcher). Never throws.
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.5); trace action {@code CONSUME_STREAM_EVENTS}.
 */
public final class BoosterEventStreamConsumer {

  private BoosterEventStreamConsumer() {}

  /**
   * {@code consume(rawEvents, config)}. Implemented by stage core-2a.
   *
   * @param rawEvents the provider-specific events
   * @param config the configuration
   */
  public static void consume(Object rawEvents, BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: BoosterEventStreamConsumer#consume");
  }
}
