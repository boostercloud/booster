package com.boostercloud.framework.core;

import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.RawEventsParser.EventsStreamingCallback;
import com.boostercloud.framework.core.services.ReadModelStore;

/**
 * Port of {@code booster-event-processor.ts}: the per-entity callback (dedup through the dispatched-events store,
 * event handlers — specific and global —, snapshot and read model projection).
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.4); trace action {@code EVENT_HANDLERS_PROCESS}.
 * User handlers are called with {@code UserCode.invokeStatic} so that {@code @Trace} on them works.
 */
public final class BoosterEventProcessor {

  private BoosterEventProcessor() {}

  /**
   * {@code eventProcessor(eventStore, readModelStore)}. Implemented by stage core-2a.
   *
   * @param eventStore the event store
   * @param readModelStore the read model store
   * @return the callback for {@code RawEventsParser.streamPerEntityEvents}
   */
  public static EventsStreamingCallback eventProcessor(EventStore eventStore, ReadModelStore readModelStore) {
    throw new UnsupportedOperationException("not implemented yet: BoosterEventProcessor#eventProcessor");
  }
}
