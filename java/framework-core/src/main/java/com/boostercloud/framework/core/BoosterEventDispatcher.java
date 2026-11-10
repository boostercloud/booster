package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code booster-event-dispatcher.ts}: the entry point of a page of raw events coming from the provider's
 * event store change feed (raw → envelopes → per-entity processing). Never throws.
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.3); trace action {@code DISPATCH_EVENTS}.
 */
public final class BoosterEventDispatcher {

  private BoosterEventDispatcher() {}

  /**
   * {@code dispatch(rawEvents, config)}. Implemented by stage core-2a.
   *
   * @param rawEvents the provider-specific events
   * @param config the configuration
   */
  public static void dispatch(Object rawEvents, BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: BoosterEventDispatcher#dispatch");
  }
}
