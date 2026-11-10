package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code booster-event-stream-producer.ts}: forwards events from the event store change feed to the event
 * stream, per entity ({@code provider.events().produce}). Never throws.
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.6); trace action {@code PRODUCE_STREAM_EVENTS}.
 */
public final class BoosterEventStreamProducer {

  private BoosterEventStreamProducer() {}

  /**
   * {@code produce(request, config)}. Implemented by stage core-2a.
   *
   * @param request the provider-specific request
   * @param config the configuration
   */
  public static void produce(Object request, BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: BoosterEventStreamProducer#produce");
  }
}
