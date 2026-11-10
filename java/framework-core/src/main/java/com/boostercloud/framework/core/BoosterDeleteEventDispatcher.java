package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;

/**
 * Port of {@code booster-delete-event-dispatcher.ts}: hard-deletes events (finds them, un-projects and deletes their
 * snapshots, then deletes the events).
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.7).
 */
public final class BoosterDeleteEventDispatcher {

  private BoosterDeleteEventDispatcher() {}

  /**
   * {@code deleteEvent(config, parameters)}. Implemented by stage core-2a.
   *
   * @param config the configuration
   * @param parameters which event
   * @return {@code false} when nothing was found, {@code true} after deleting
   */
  public static boolean deleteEvent(BoosterConfig config, EventDeleteParameters parameters) {
    throw new UnsupportedOperationException("not implemented yet: BoosterDeleteEventDispatcher#deleteEvent");
  }
}
